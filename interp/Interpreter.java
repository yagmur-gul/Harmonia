package interp;

import ast.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* Tree-walking interpreter for Harmonia. Built up across steps; this revision
 * contains the run-time state, the player fan-out, and full expression
 * evaluation. Statement execution and the program driver arrive next. */
public final class Interpreter {

    /** Callable declarations, gathered once before execution. */
    private final Map<String, MelodyDecl> melodies = new HashMap<>();
    private final Map<String, FuncDecl>   funcs    = new HashMap<>();

    /** The output sinks; every musical action is broadcast to all of them. */
    private final List<NotePlayer> players;

    /** The global scope — top-level variables live here for the whole run. */
    private final Environment globals = new Environment();

    /**
     * Current transpose offset in semitones. {@code transpose N { ... }} adds N
     * while the block runs; nested transposes stack additively, so we keep a
     * single running total and save/restore it around each block.
     */
    private int transposeOffset = 0;

    /** Current tempo in BPM; the default until a tempo statement runs. */
    private int currentBpm = 120;

    public Interpreter(List<NotePlayer> players) {
        this.players = players;
    }

    // ====================================================================
    // Expression evaluation
    // Each returns a boxed Java value: Integer, Double, Boolean,
    // Values.Note, Values.Event, or Values.Phrase.
    // ====================================================================

    Object eval(Expr e, Environment env) {
        return switch (e) {
            case IntLit i    -> i.value();            // -> Integer
            case FloatLit f  -> f.value();            // -> Double
            case BoolLit b   -> b.value();            // -> Boolean
            case NoteLit n   -> new Values.Note(Pitch.toMidi(n));
            case Ident id    -> env.get(id.name());
            case Grouping g  -> eval(g.inner(), env);
            case Unary u     -> evalUnary(u, env);
            case BinOp b     -> evalBinOp(b, env);
            case Call c      -> evalCall(c, env);
            case PhraseLit p -> evalPhraseLit(p, env);
            case EventLit ev -> evalEventLit(ev, env);
            case Index ix    -> evalIndex(ix, env);
        };
    }

    private Object evalUnary(Unary u, Environment env) {
        Object v = eval(u.operand(), env);
        return switch (u.op()) {
            // NEG preserves the operand's type: Integer stays int, Double stays
            // float. The type checker guaranteed v is numeric here.
            case NEG -> (v instanceof Integer i) ? (Object) (-i) : (Object) (-(Double) v);
            case NOT -> !(Boolean) v;
        };
    }

    private Object evalBinOp(BinOp b, Environment env) {
        // && and || must short-circuit, so they evaluate the right operand only
        // when needed — handled before the left operand is forced for the rest.
        switch (b.op()) {
            case AND -> {
                boolean left = (Boolean) eval(b.left(), env);
                return left && (Boolean) eval(b.right(), env);
            }
            case OR -> {
                boolean left = (Boolean) eval(b.left(), env);
                return left || (Boolean) eval(b.right(), env);
            }
            default -> { /* fall through to eager evaluation below */ }
        }

        // Every other operator evaluates both sides, left first (the language
        // fixes left-to-right operand order).
        Object l = eval(b.left(), env);
        Object r = eval(b.right(), env);

        return switch (b.op()) {
            case ADD, SUB, MUL, DIV -> arithmetic(b, l, r);
            case LT, LE, GT, GE     -> compareOrder(b.op(), l, r);
            case EQ                 -> equalValues(l, r);
            case NEQ                -> !equalValues(l, r);
            // AND/OR already returned above; listing them keeps the switch
            // exhaustive over the enum.
            case AND, OR -> throw new IllegalStateException("unreachable");
        };
    }

    /**
     * The four arithmetic operators. If either operand is a Double the result
     * is real (float); otherwise both are Integer and the result is integer
     * (so int / int is integer division — the D1 rule). Division checks the
     * divisor for zero in *both* cases: Java would throw on integer / 0 but
     * silently yield Infinity on 1.0 / 0.0, so we test explicitly to make both
     * an error with a line number.
     */
    private Object arithmetic(BinOp b, Object l, Object r) {
        boolean real = (l instanceof Double) || (r instanceof Double);
        if (b.op() == Expr.BinaryOp.DIV && isZero(r)) {
            throw new RuntimeError("division by zero", b.line(), b.column());
        }
        if (real) {
            double x = toDouble(l), y = toDouble(r);
            return switch (b.op()) {
                case ADD -> x + y; case SUB -> x - y;
                case MUL -> x * y; case DIV -> x / y;
                default  -> throw new IllegalStateException("unreachable");
            };
        } else {
            int x = (Integer) l, y = (Integer) r;
            return switch (b.op()) {
                case ADD -> x + y; case SUB -> x - y;
                case MUL -> x * y; case DIV -> x / y; // integer division
                default  -> throw new IllegalStateException("unreachable");
            };
        }
    }

    /** Ordering comparisons; compared as doubles, which also bridges int/float. */
    private Boolean compareOrder(Expr.BinaryOp op, Object l, Object r) {
        double x = toDouble(l), y = toDouble(r);
        return switch (op) {
            case LT -> x < y;  case LE -> x <= y;
            case GT -> x > y;  case GE -> x >= y;
            default -> throw new IllegalStateException("unreachable");
        };
    }

    /**
     * Value equality for == / !=. Numbers compare by numeric value (so 1 == 1.0
     * is true, via coercion to double); bools and notes compare with .equals
     * (Note is a record, so that is value equality on the MIDI number). These
     * are the only equality-comparable types the checker allowed.
     */
    private boolean equalValues(Object l, Object r) {
        if (isNumber(l) && isNumber(r)) {
            return toDouble(l) == toDouble(r);
        }
        return l.equals(r);
    }

    private Object evalCall(Call c, Environment env) {
        // A Call expression always names a func (melodies are void and reached
        // only via play). The function runs in its own scope chained to globals
        // — static scoping — never to the caller's locals.
        FuncDecl f = funcs.get(c.callee());
        List<Object> args = evalArgs(c.args(), env);
        return callFunc(f, args);
    }

    private Values.Phrase evalPhraseLit(PhraseLit p, Environment env) {
        List<Values.Event> events = new ArrayList<>();
        for (Expr el : p.elements()) {
            // Every element is an EventLit (the checker proved it), so each
            // eval yields a Values.Event.
            events.add((Values.Event) eval(el, env));
        }
        return new Values.Phrase(events);
    }

    private Values.Event evalEventLit(EventLit ev, Environment env) {
        Values.Note pitch = (Values.Note) eval(ev.pitch(), env);
        return new Values.Event(pitch, ev.duration());
    }

    private Object evalIndex(Index ix, Environment env) {
        Values.Phrase phrase = (Values.Phrase) eval(ix.target(), env);
        int i = (Integer) eval(ix.index(), env);
        List<Values.Event> events = phrase.events();
        if (i < 0 || i >= events.size()) {
            throw new RuntimeError(
                "phrase index " + i + " out of range (phrase has "
                    + events.size() + " event(s))",
                ix.line(), ix.column());
        }
        return events.get(i); // an Event (matches the checker's EVENT result)
    }

    // ---- small numeric helpers ----

    private static boolean isNumber(Object v) { return v instanceof Integer || v instanceof Double; }

    private static boolean isZero(Object v) {
        return (v instanceof Integer i && i == 0) || (v instanceof Double d && d == 0.0);
    }

    /** Widen an int or float value to double for mixed-type arithmetic/compare. */
    private static double toDouble(Object v) {
        return (v instanceof Integer i) ? (double) i : (Double) v;
    }

    /**
     * Reduce a numeric value (int or float) to an int, for domain statements
     * whose operand the type checker allows to be either (tempo BPM, volume
     * velocity). A float is rounded to the nearest integer.
     */
    private static int asInt(Object v) {
        return (v instanceof Integer i) ? i : (int) Math.round((Double) v);
    }

    private List<Object> evalArgs(List<Expr> argExprs, Environment env) {
        List<Object> args = new ArrayList<>();
        for (Expr a : argExprs) {
            args.add(eval(a, env));
        }
        return args;
    }

    // ====================================================================
    // Program driver
    // ====================================================================

    /**
     * Run a whole program. Two passes: first register every melody/func so a
     * call can refer to one declared later in the file; then execute the
     * top-level items (var decls and play statements) in source order, in the
     * global scope. begin()/end() bracket the players around the run.
     */
    public void run(Program program) {
        for (TopLevel item : program.items()) {
            switch (item) {
                case MelodyDecl m -> melodies.put(m.name(), m);
                case FuncDecl   f -> funcs.put(f.name(), f);
                default -> { /* var decls and plays run in pass two */ }
            }
        }

        for (NotePlayer p : players) p.begin();
        try {
            for (TopLevel item : program.items()) {
                switch (item) {
                    case VarDecl v -> exec(v, globals);
                    case Play    p -> exec(p, globals);
                    default -> { /* declarations were handled in pass one */ }
                }
            }
        } finally {
            // end() runs even if a RuntimeError aborts the program, so the synth
            // and file handle are always released / flushed.
            for (NotePlayer p : players) p.end();
        }
    }

    // ====================================================================
    // Statement execution
    // ====================================================================

    private void exec(Stmt s, Environment env) {
        switch (s) {
            case VarDecl v   -> execVarDecl(v, env);
            case Assign a    -> env.assign(a.name(), eval(a.value(), env));
            case If i        -> execIf(i, env);
            case Repeat r    -> execRepeat(r, env);
            case Return ret  -> execReturn(ret, env);
            case Block b     -> execBlock(b, new Environment(env));
            case Tempo t     -> execTempo(t, env);
            case Volume vol  -> execVolume(vol, env);
            case NoteStmt n  -> execNote(n, env);
            case Transpose t -> execTranspose(t, env);
            case Play p      -> execPlay(p, env);
        }
    }

    private void execVarDecl(VarDecl v, Environment env) {
        // The grammar allows a declaration with no initializer (e.g. `int x;`).
        // Such a variable starts unbound (null); the type checker ensures it is
        // assigned before use, so we never read a null as a real value.
        Object value = (v.init() == null) ? null : eval(v.init(), env);
        if (value != null) {
            // Coerce at the binding point: an int initializer for a float
            // variable must be stored as a Double, or later float math would
            // treat it as int.
            value = coerce(value, v.type());
        }
        env.define(v.name(), value);
    }

    private void execIf(If i, Environment env) {
        boolean cond = (Boolean) eval(i.cond(), env);
        if (cond) {
            execBlock(i.thenBlock(), new Environment(env));
        } else if (i.elseBranch() != null) {
            // else branch is either a Block or another If (else-if chain). An If
            // runs in the current scope; a Block opens its own.
            if (i.elseBranch() instanceof Block b) {
                execBlock(b, new Environment(env));
            } else {
                exec(i.elseBranch(), env);
            }
        }
    }

    private void execRepeat(Repeat r, Environment env) {
        int n = (Integer) eval(r.count(), env);
        // Each iteration opens a fresh scope, so locals declared in the body do
        // not leak between iterations (matching the "repeat opens a scope" rule;
        // the type checker checked the body once in one such scope).
        for (int k = 0; k < n; k++) {
            execBlock(r.body(), new Environment(env));
        }
    }

    private void execReturn(Return ret, Environment env) {
        Object value = (ret.value() == null) ? null : eval(ret.value(), env);
        throw new ReturnSignal(value);
    }

    /** Run a block's statements in the given (already-created) scope. */
    private void execBlock(Block b, Environment scope) {
        for (Stmt st : b.statements()) {
            exec(st, scope);
        }
    }

    // ---- domain statements ----

    private void execTempo(Tempo t, Environment env) {
        // The type checker now requires tempo to be int (BPM is a whole
        // number), so the value is an Integer; asInt is just an identity here.
        currentBpm = asInt(eval(t.value(), env));
        for (NotePlayer p : players) p.setTempo(currentBpm);
    }

    private void execVolume(Volume vol, Environment env) {
        // Likewise volume is required to be int (MIDI velocity); asInt is an
        // identity here, kept for symmetry with the rounding helper.
        int velocity = asInt(eval(vol.value(), env));
        for (NotePlayer p : players) p.setVolume(velocity);
    }

    private void execNote(NoteStmt n, Environment env) {
        Values.Note note = (Values.Note) eval(n.pitch(), env);
        soundEvent(note.midi(), n.duration(), n.line(), n.column());
    }

    private void execTranspose(Transpose t, Environment env) {
        int amount = (Integer) eval(t.amount(), env);
        int saved = transposeOffset;       // save, so nested transposes stack
        transposeOffset += amount;         //   additively and restore cleanly
        try {
            execBlock(t.body(), new Environment(env));
        } finally {
            transposeOffset = saved;       // restore even if a RuntimeError fires
        }
    }

    /**
     * The single point where a pitch becomes sound. Applies the current
     * transpose offset, enforces the MIDI 0–127 range (the domain runtime
     * error), converts the duration to milliseconds at the current tempo, and
     * broadcasts to every player so all outputs stay identical.
     */
    private void soundEvent(int baseMidi, Duration dur, int line, int col) {
        int midi = baseMidi + transposeOffset;
        if (midi < 0 || midi > 127) {
            throw new RuntimeError(
                "transpose pushed note " + Pitch.midiToName(baseMidi)
                    + " (MIDI " + baseMidi + ") by " + transposeOffset
                    + " semitones to MIDI " + midi
                    + ", which is outside the playable range 0–127",
                line, col);
        }
        int ms = durationMs(dur);
        for (NotePlayer p : players) p.playNote(midi, ms);
    }

    /** beats(dur) * (60000 / BPM); quarter = 1 beat is the agreed mapping. */
    private int durationMs(Duration dur) {
        double beats = switch (dur) {
            case WHOLE   -> 4.0;
            case HALF    -> 2.0;
            case QUARTER -> 1.0;
            case EIGHTH  -> 0.5;
        };
        return (int) Math.round(beats * (60000.0 / currentBpm));
    }

    // ---- play ----

    private void execPlay(Play p, Environment env) {
        if (p.parenthesized()) {
            // play name(args);  -> a melody (void). Runs for its effects.
            MelodyDecl m = melodies.get(p.callee());
            List<Object> args = evalArgs(p.args(), env);
            callMelody(m, args);
        } else {
            // play name;  -> name is a phrase variable; sound each event.
            Values.Phrase phrase = (Values.Phrase) env.get(p.callee());
            for (Values.Event ev : phrase.events()) {
                soundEvent(ev.pitch().midi(), ev.duration(), p.line(), p.column());
            }
        }
    }

    // ====================================================================
    // Call machinery (pass-by-value, static scoping)
    // ====================================================================

    /**
     * Bind arguments to parameters in a fresh scope chained to GLOBALS (not to
     * the caller — static scoping). Pass-by-value: arguments were already
     * evaluated to values; we copy each into the new scope, coercing int->float
     * where the parameter is float. The body runs directly in this scope (the
     * type checker did not open a second scope on top of the parameter scope).
     */
    private Environment bindParams(List<Param> params, List<Object> args) {
        Environment scope = new Environment(globals);
        for (int k = 0; k < params.size(); k++) {
            Param prm = params.get(k);
            scope.define(prm.name(), coerce(args.get(k), prm.type()));
        }
        return scope;
    }

    private void callMelody(MelodyDecl m, List<Object> args) {
        Environment scope = bindParams(m.params(), args);
        // A bare `return;` inside a melody unwinds to here and simply stops it.
        try {
            execBlock(m.body(), scope);
        } catch (ReturnSignal r) {
            /* melody finished early; value is always null */
        }
    }

    private Object callFunc(FuncDecl f, List<Object> args) {
        Environment scope = bindParams(f.params(), args);
        try {
            execBlock(f.body(), scope);
        } catch (ReturnSignal r) {
            // Coerce the returned value to the declared return type, so a func
            // declared float that returns an int literal yields a Double.
            return coerce(r.value, f.returnType());
        }
        // The type checker guarantees every func path returns, so this is a
        // should-never-happen guard.
        throw new IllegalStateException(
            "func '" + f.name() + "' finished without returning");
    }

    /** Widen an int value to float when the target type is FLOAT; else as-is. */
    private static Object coerce(Object value, Type target) {
        if (target == Type.FLOAT && value instanceof Integer i) {
            return (double) i;
        }
        return value;
    }
}