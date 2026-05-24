package check;

import ast.*;
import java.util.List;

// Static type checker for Harmonia.
public final class TypeChecker {

    private final Program program;
    private final TypeEnv env = new TypeEnv();

    // The declared return type of the func whose body we are currently inside,
    // or null when we are inside a melody (void) or at top level. Used to check
    // `return` statements.
    private Type currentFuncReturn = null;
    private boolean inMelody = false;

    public TypeChecker(Program program) {
        this.program = program;
    }

    // Entry point. Throws TypeError on the first problem found.
    public void check() {
        collectSignatures();   // pass 1
        for (TopLevel item : program.items()) {   // pass 2
            checkTopLevel(item);
        }
    }

    // ---------- pass 1: gather callable signatures ----------

    private void collectSignatures() {
        for (TopLevel item : program.items()) {
            switch (item) {
                case MelodyDecl m -> {
                    List<Type> ps = paramTypes(m.params());
                    if (!env.declareMelody(m.name(), new TypeEnv.MelodySig(ps))) {
                        throw new TypeError("'" + m.name() + "' is already declared",
                                            m.line(), m.column());
                    }
                }
                case FuncDecl f -> {
                    List<Type> ps = paramTypes(f.params());
                    if (!env.declareFunc(f.name(),
                            new TypeEnv.FuncSig(f.returnType(), ps))) {
                        throw new TypeError("'" + f.name() + "' is already declared",
                                            f.line(), f.column());
                    }
                }
                // Top-level var decls and play statements carry no signature.
                case VarDecl v -> { }
                case Play p     -> { }
            }
        }
    }

    private static List<Type> paramTypes(List<Param> params) {
        return params.stream().map(Param::type).toList();
    }

    // ---------- the one coercion rule ----------

    // Is a value of type `from` acceptable where type `to` is required?
    // Identical types are always fine; additionally int widens to float.
    // Nothing else coerces (in particular float never narrows to int).
    private static boolean assignable(Type from, Type to) {
        if (from == to) return true;
        return from == Type.INT && to == Type.FLOAT;
    }

    private static boolean isNumeric(Type t) {
        return t == Type.INT || t == Type.FLOAT;
    }

    // Pretty type name for error messages.
    private static String name(Type t) {
        return t.name().toLowerCase();
    }

    // ---------- pass 2 dispatch: top level ----------

    private void checkTopLevel(TopLevel item) {
        switch (item) {
            case VarDecl v    -> checkVarDecl(v);
            case MelodyDecl m -> checkMelodyDecl(m);
            case FuncDecl f   -> checkFuncDecl(f);
            case Play p       -> checkPlay(p);
        }
    }

    private void checkMelodyDecl(MelodyDecl m) {
        env.pushScope();
        boolean savedInMelody = inMelody;
        Type savedReturn = currentFuncReturn;
        inMelody = true;
        currentFuncReturn = null;

        for (Param p : m.params()) {
            if (!env.declareVar(p.name(), p.type())) {
                throw new TypeError("duplicate parameter '" + p.name() + "'",
                                    p.line(), p.column());
            }
        }
        // The body is a Block; check its statements in the scope we just opened
        // (don't open a second scope for the block itself).
        for (Stmt s : m.body().statements()) {
            checkStmt(s);
        }

        inMelody = savedInMelody;
        currentFuncReturn = savedReturn;
        env.popScope();
    }

    private void checkFuncDecl(FuncDecl f) {
        env.pushScope();
        boolean savedInMelody = inMelody;
        Type savedReturn = currentFuncReturn;
        inMelody = false;
        currentFuncReturn = f.returnType();

        for (Param p : f.params()) {
            if (!env.declareVar(p.name(), p.type())) {
                throw new TypeError("duplicate parameter '" + p.name() + "'",
                                    p.line(), p.column());
            }
        }
        for (Stmt s : f.body().statements()) {
            checkStmt(s);
        }

        inMelody = savedInMelody;
        currentFuncReturn = savedReturn;
        env.popScope();
    }

    // ---------- statements ----------

    private void checkStmt(Stmt s) {
        switch (s) {
            case VarDecl v   -> checkVarDecl(v);
            case Assign a    -> checkAssign(a);
            case If f        -> checkIf(f);
            case Repeat r    -> checkRepeat(r);
            case Return r    -> checkReturn(r);
            case Tempo t     -> checkTempo(t);
            case Volume vol  -> checkVolume(vol);
            case NoteStmt n  -> checkNoteStmt(n);
            case Play p      -> checkPlay(p);
            case Transpose t -> checkTranspose(t);
            case Block b     -> checkBlock(b);
        }
    }

    private void checkBlock(Block b) {
        env.pushScope();
        for (Stmt s : b.statements()) {
            checkStmt(s);
        }
        env.popScope();
    }

    private void checkVarDecl(VarDecl v) {
        // Declare first or check init first? We check the initializer in the
        // current environment *before* binding the name, so `int x = x;`
        // (referring to itself) is a use-before-declaration error rather than
        // silently binding to itself.
        if (v.init() != null) {
            Type initType = checkExpr(v.init());
            if (!assignable(initType, v.type())) {
                throw new TypeError(
                    "cannot initialize " + name(v.type()) + " variable '" + v.name()
                        + "' with a value of type " + name(initType),
                    v.line(), v.column());
            }
        }
        if (!env.declareVar(v.name(), v.type())) {
            throw new TypeError("'" + v.name() + "' is already declared in this scope",
                                v.line(), v.column());
        }
    }

    private void checkAssign(Assign a) {
        Type target = env.lookupVar(a.name());
        if (target == null) {
            throw new TypeError("assignment to undeclared variable '" + a.name() + "'",
                                a.line(), a.column());
        }
        Type valueType = checkExpr(a.value());
        if (!assignable(valueType, target)) {
            throw new TypeError(
                "cannot assign a value of type " + name(valueType)
                    + " to " + name(target) + " variable '" + a.name() + "'",
                a.line(), a.column());
        }
    }

    private void checkIf(If f) {
        Type cond = checkExpr(f.cond());
        if (cond != Type.BOOL) {
            throw new TypeError(
                "if condition must be bool, but is " + name(cond),
                f.line(), f.column());
        }
        checkBlock(f.thenBlock());
        if (f.elseBranch() != null) {
            checkStmt(f.elseBranch());  // either another If (else-if) or a Block
        }
    }

    private void checkRepeat(Repeat r) {
        Type count = checkExpr(r.count());
        // repeat count must be int: a fractional number of iterations is
        // meaningless, and we deliberately do NOT coerce here.
        if (count != Type.INT) {
            throw new TypeError(
                "repeat count must be int, but is " + name(count),
                r.line(), r.column());
        }
        checkBlock(r.body());
    }

    private void checkReturn(Return r) {
        if (inMelody) {
            // melodies are void: a bare `return;` is fine, `return expr;` is not.
            if (r.value() != null) {
                throw new TypeError(
                    "a melody is void and cannot return a value",
                    r.line(), r.column());
            }
            return;
        }
        // inside a func
        if (currentFuncReturn == null) {
            throw new TypeError("return statement outside of a function",
                                r.line(), r.column());
        }
        if (r.value() == null) {
            throw new TypeError(
                "function must return a value of type " + name(currentFuncReturn),
                r.line(), r.column());
        }
        Type actual = checkExpr(r.value());
        if (!assignable(actual, currentFuncReturn)) {
            throw new TypeError(
                "function returns " + name(currentFuncReturn)
                    + " but this return value is " + name(actual),
                r.line(), r.column());
        }
    }

    private void checkTempo(Tempo t) {
        Type v = checkExpr(t.value());
        if (v != Type.INT) {
            throw new TypeError("tempo expects an int (BPM), but got " + name(v),
                                t.line(), t.column());
        }
    }

    private void checkVolume(Volume vol) {
        Type v = checkExpr(vol.value());
        if (v != Type.INT) {
            throw new TypeError("volume expects an int (velocity), but got " + name(v),
                                vol.line(), vol.column());
        }
    }

    private void checkNoteStmt(NoteStmt n) {
        Type p = checkExpr(n.pitch());
        if (p != Type.NOTE) {
            throw new TypeError(
                "note statement expects a note pitch, but got " + name(p),
                n.line(), n.column());
        }
        // duration is a keyword, already validated by the parser.
    }

    private void checkTranspose(Transpose t) {
        Type amount = checkExpr(t.amount());
        // transpose amount is a number of semitones: int, no coercion.
        if (amount != Type.INT) {
            throw new TypeError(
                "transpose amount must be int (semitones), but is " + name(amount),
                t.line(), t.column());
        }
        checkBlock(t.body());
    }

    private void checkPlay(Play p) {
        if (p.parenthesized()) {
            // call form: play foo(args);  -> foo must be a MELODY
            TypeEnv.MelodySig sig = env.lookupMelody(p.callee());
            if (sig == null) {
                if (env.lookupFunc(p.callee()) != null) {
                    throw new TypeError(
                        "'" + p.callee() + "' is a function, not a melody; "
                            + "only melodies can be played",
                        p.line(), p.column());
                }
                Type asVar = env.lookupVar(p.callee());
                if (asVar == Type.PHRASE) {
                    throw new TypeError(
                        "'" + p.callee() + "' is a phrase; play it without parentheses: "
                            + "play " + p.callee() + ";",
                        p.line(), p.column());
                }
                throw new TypeError("no melody named '" + p.callee() + "'",
                                    p.line(), p.column());
            }
            checkArgs(p.callee(), sig.paramTypes(), p.args(), p.line(), p.column());
        } else {
            // bare form: play intro;  -> intro must be a phrase VARIABLE
            Type t = env.lookupVar(p.callee());
            if (t == null) {
                throw new TypeError("no variable named '" + p.callee() + "' to play",
                                    p.line(), p.column());
            }
            if (t != Type.PHRASE) {
                throw new TypeError(
                    "play x; expects x to be a phrase, but '" + p.callee()
                        + "' is " + name(t),
                    p.line(), p.column());
            }
        }
    }

    // Shared argument-count and argument-type check for melody/func calls.
    private void checkArgs(String callee, List<Type> params, List<Expr> args,
                           int line, int column) {
        if (args.size() != params.size()) {
            throw new TypeError(
                "'" + callee + "' expects " + params.size() + " argument(s) but got "
                    + args.size(),
                line, column);
        }
        for (int i = 0; i < args.size(); i++) {
            Type argType = checkExpr(args.get(i));
            Type paramType = params.get(i);
            if (!assignable(argType, paramType)) {
                throw new TypeError(
                    "argument " + (i + 1) + " of '" + callee + "' expects "
                        + name(paramType) + " but got " + name(argType),
                    line, column);
            }
        }
    }

    // ---------- expressions ----------
    // Each returns the Type of the expression, or throws TypeError.

    private Type checkExpr(Expr e) {
        return switch (e) {
            case IntLit i    -> Type.INT;
            case FloatLit f  -> Type.FLOAT;
            case BoolLit b   -> Type.BOOL;
            case NoteLit n   -> Type.NOTE;
            case Ident id    -> checkIdent(id);
            case Grouping g  -> checkExpr(g.inner());
            case Unary u     -> checkUnary(u);
            case BinOp b     -> checkBinOp(b);
            case Call c      -> checkCall(c);
            case PhraseLit p -> checkPhraseLit(p);
            case EventLit ev -> checkEventLit(ev);
            case Index ix    -> checkIndex(ix);
        };
    }

    private Type checkIdent(Ident id) {
        Type t = env.lookupVar(id.name());
        if (t == null) {
            throw new TypeError("undeclared variable '" + id.name() + "'",
                                id.line(), id.column());
        }
        return t;
    }

    private Type checkUnary(Unary u) {
        Type operand = checkExpr(u.operand());
        return switch (u.op()) {
            case NEG -> {
                if (!isNumeric(operand)) {
                    throw new TypeError(
                        "unary '-' expects a number, but got " + name(operand),
                        u.line(), u.column());
                }
                yield operand;   // int -> int, float -> float
            }
            case NOT -> {
                if (operand != Type.BOOL) {
                    throw new TypeError(
                        "unary '!' expects bool, but got " + name(operand),
                        u.line(), u.column());
                }
                yield Type.BOOL;
            }
        };
    }

    private Type checkBinOp(BinOp b) {
        Type left  = checkExpr(b.left());
        Type right = checkExpr(b.right());
        return switch (b.op()) {
            // arithmetic: both numeric; result is float if either is float,
            // else int.
            case ADD, SUB, MUL, DIV -> {
                if (!isNumeric(left) || !isNumeric(right)) {
                    throw new TypeError(
                        "operator '" + opSym(b.op()) + "' expects two numbers, but got "
                            + name(left) + " and " + name(right),
                        b.line(), b.column());
                }
                yield (left == Type.FLOAT || right == Type.FLOAT)
                        ? Type.FLOAT : Type.INT;
            }
            // ordering: numeric only -> bool
            case LT, LE, GT, GE -> {
                if (!isNumeric(left) || !isNumeric(right)) {
                    throw new TypeError(
                        "operator '" + opSym(b.op())
                            + "' expects two numbers, but got "
                            + name(left) + " and " + name(right),
                        b.line(), b.column());
                }
                yield Type.BOOL;
            }
            // equality: operands must be comparable -> bool.
            // numeric vs numeric (via coercion), bool vs bool, note vs note.
            case EQ, NEQ -> {
                if (!equalityComparable(left, right)) {
                    throw new TypeError(
                        "operator '" + opSym(b.op())
                            + "' cannot compare " + name(left) + " and " + name(right),
                        b.line(), b.column());
                }
                yield Type.BOOL;
            }
            // logical: bool only -> bool
            case AND, OR -> {
                if (left != Type.BOOL || right != Type.BOOL) {
                    throw new TypeError(
                        "operator '" + opSym(b.op()) + "' expects two bools, but got "
                            + name(left) + " and " + name(right),
                        b.line(), b.column());
                }
                yield Type.BOOL;
            }
        };
    }

    // Two types may be compared with == / != when they are the same type,
    // or both numeric (int/float, coercion bridges them). Notes compare by
    // equality (a music-domain "is this the same note" question); phrases and
    // events are not equality-comparable in this version.
    private static boolean equalityComparable(Type a, Type b) {
        if (isNumeric(a) && isNumeric(b)) return true;
        if (a == Type.BOOL && b == Type.BOOL) return true;
        if (a == Type.NOTE && b == Type.NOTE) return true;
        return false;
    }

    private Type checkCall(Call c) {
        // A Call expression names a func (melodies are never called inside an
        // expression -- they are void and go through `play`).
        TypeEnv.FuncSig sig = env.lookupFunc(c.callee());
        if (sig == null) {
            if (env.lookupMelody(c.callee()) != null) {
                throw new TypeError(
                    "'" + c.callee() + "' is a melody (void); it cannot be used "
                        + "in an expression -- use 'play " + c.callee() + "(...);'",
                    c.line(), c.column());
            }
            throw new TypeError("call to undeclared function '" + c.callee() + "'",
                                c.line(), c.column());
        }
        checkArgs(c.callee(), sig.paramTypes(), c.args(), c.line(), c.column());
        return sig.returnType();
    }

    private Type checkPhraseLit(PhraseLit p) {
        // Every element must be an event. The parser builds EventLit nodes here,
        // but we still type-check each one (its pitch must be a note).
        for (Expr el : p.elements()) {
            Type elemType = checkExpr(el);
            if (elemType != Type.EVENT) {
                throw new TypeError(
                    "phrase elements must be events, but found " + name(elemType),
                    p.line(), p.column());
            }
        }
        return Type.PHRASE;
    }

    private Type checkEventLit(EventLit ev) {
        Type pitch = checkExpr(ev.pitch());
        if (pitch != Type.NOTE) {
            throw new TypeError(
                "an event's pitch must be a note, but got " + name(pitch),
                ev.line(), ev.column());
        }
        return Type.EVENT;
    }

    private Type checkIndex(Index ix) {
        Type target = checkExpr(ix.target());
        if (target != Type.PHRASE) {
            throw new TypeError(
                "indexing with [ ] is only allowed on a phrase, but got "
                    + name(target),
                ix.line(), ix.column());
        }
        Type idx = checkExpr(ix.index());
        if (idx != Type.INT) {
            throw new TypeError(
                "a phrase index must be int, but is " + name(idx),
                ix.line(), ix.column());
        }
        // Bounds are checked at run time, not here. Indexing a phrase yields
        // an event (the duration is preserved -- D1, Entry 9).
        return Type.EVENT;
    }

    // Operator symbol for messages.
    private static String opSym(Expr.BinaryOp op) {
        return switch (op) {
            case ADD -> "+";  case SUB -> "-";  case MUL -> "*"; case DIV -> "/";
            case EQ  -> "=="; case NEQ -> "!="; case LT  -> "<"; case LE  -> "<=";
            case GT  -> ">";  case GE  -> ">=";
            case AND -> "&&"; case OR  -> "||";
        };
    }
}