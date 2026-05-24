package util;

import ast.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Prints a Harmonia AST as an indented tree, e.g.
 *
 * <pre>
 *   Program
 *   └── MelodyDecl name=intro
 *       └── Block
 *           └── NoteStmt pitch=C4 dur=quarter
 * </pre>
 *
 * Each node's class name leads the header line. Atomic data (enums,
 * primitives, names, and leaf expressions like {@link IntLit},
 * {@link FloatLit}, {@link BoolLit}, {@link NoteLit}, {@link Ident}) is
 * rendered inline as space-separated {@code key=value} pairs on the
 * header. Composite children (statements, blocks, non-leaf expressions,
 * and lists of nodes) become indented child lines. Source positions
 * (line / column) are intentionally omitted.
 *
 * Dispatch uses Java 21 pattern matching on the sealed interfaces
 * {@link TopLevel}, {@link Decl}, {@link Stmt}, and {@link Expr}. None of
 * those switches has a default branch — adding a new permitted subtype to
 * any of those sealed interfaces without updating this file is a
 * compile-time error.
 */
public final class ASTPrinter {

    private ASTPrinter() { }

    /** Print the AST rooted at {@code ast} to stdout. */
    public static void print(Program ast) {
        System.out.println("Program");
        List<TopLevel> items = ast.items();
        for (int i = 0; i < items.size(); i++) {
            printTopLevel(items.get(i), "", i == items.size() - 1);
        }
    }

    // ---------------------------------------------------------------------
    // Sealed-interface dispatchers. Each prints the node's header at
    // (prefix, isLast), then recurses on its composite children with an
    // extended prefix.
    // ---------------------------------------------------------------------

    private static void printTopLevel(TopLevel node, String prefix, boolean isLast) {
        switch (node) {
            case Decl d -> printDecl(d, prefix, isLast);
            case Play p -> printPlay(p, prefix, isLast);
        }
    }

    private static void printDecl(Decl node, String prefix, boolean isLast) {
        switch (node) {
            case MelodyDecl m -> {
                emit(prefix, isLast, "MelodyDecl name=" + m.name());
                String cp = extend(prefix, isLast);
                List<Object> children = new ArrayList<>();
                children.addAll(m.params());
                children.add(m.body());
                emitMixed(children, cp);
            }
            case FuncDecl f -> {
                emit(prefix, isLast,
                        "FuncDecl name=" + f.name() + " returnType=" + f.returnType());
                String cp = extend(prefix, isLast);
                List<Object> children = new ArrayList<>();
                children.addAll(f.params());
                children.add(f.body());
                emitMixed(children, cp);
            }
            case VarDecl v -> printVarDecl(v, prefix, isLast);
        }
    }

    private static void printStmt(Stmt node, String prefix, boolean isLast) {
        switch (node) {
            case VarDecl v -> printVarDecl(v, prefix, isLast);

            case Assign a -> {
                emit(prefix, isLast, "Assign name=" + a.name());
                printExpr(a.value(), extend(prefix, isLast), true, null);
            }

            case If i -> {
                emit(prefix, isLast, "If");
                String cp = extend(prefix, isLast);
                boolean hasElse = i.elseBranch() != null;
                printExpr(i.cond(), cp, false, "cond");
                printStmt(i.thenBlock(), cp, !hasElse);
                if (hasElse) {
                    // elseBranch is a Stmt — either Block or another If (else-if chain).
                    printStmt(i.elseBranch(), cp, true);
                }
            }

            case Repeat r -> {
                emit(prefix, isLast, "Repeat");
                String cp = extend(prefix, isLast);
                printExpr(r.count(), cp, false, "count");
                printStmt(r.body(),  cp, true);
            }

            case Return r -> {
                emit(prefix, isLast, "Return");
                if (r.value() != null) {
                    printExpr(r.value(), extend(prefix, isLast), true, null);
                }
            }

            case Tempo t -> {
                emit(prefix, isLast, "Tempo");
                printExpr(t.value(), extend(prefix, isLast), true, null);
            }

            case Volume v -> {
                emit(prefix, isLast, "Volume");
                printExpr(v.value(), extend(prefix, isLast), true, null);
            }

            case NoteStmt n -> {
                String pitchInline = inlineExpr(n.pitch());
                if (pitchInline != null) {
                    emit(prefix, isLast,
                            "NoteStmt pitch=" + pitchInline + " dur=" + durationName(n.duration()));
                } else {
                    emit(prefix, isLast, "NoteStmt dur=" + durationName(n.duration()));
                    printExpr(n.pitch(), extend(prefix, isLast), true, "pitch");
                }
            }

            case Play p -> printPlay(p, prefix, isLast);

            case Transpose t -> {
                emit(prefix, isLast, "Transpose");
                String cp = extend(prefix, isLast);
                printExpr(t.amount(), cp, false, "amount");
                printStmt(t.body(),   cp, true);
            }

            case Block b -> {
                emit(prefix, isLast, "Block");
                String cp = extend(prefix, isLast);
                List<Stmt> stmts = b.statements();
                for (int i = 0; i < stmts.size(); i++) {
                    printStmt(stmts.get(i), cp, i == stmts.size() - 1);
                }
            }
        }
    }

    private static void printExpr(Expr node, String prefix, boolean isLast, String label) {
        switch (node) {
            case IntLit i   -> emit(prefix, isLast, header(label, "IntLit value="   + i.value()));
            case FloatLit f -> emit(prefix, isLast, header(label, "FloatLit value=" + f.value()));
            case BoolLit b  -> emit(prefix, isLast, header(label, "BoolLit value="  + b.value()));
            case NoteLit n  -> emit(prefix, isLast, header(label, "NoteLit value="  + noteLitInline(n)));
            case Ident id   -> emit(prefix, isLast, header(label, "Ident name="     + id.name()));

            case Grouping g -> {
                emit(prefix, isLast, header(label, "Grouping"));
                printExpr(g.inner(), extend(prefix, isLast), true, null);
            }
            case Unary u -> {
                emit(prefix, isLast, header(label, "Unary op=" + unarySymbol(u.op())));
                printExpr(u.operand(), extend(prefix, isLast), true, null);
            }
            case BinOp b -> {
                emit(prefix, isLast, header(label, "BinOp op=" + binarySymbol(b.op())));
                String cp = extend(prefix, isLast);
                printExpr(b.left(),  cp, false, "left");
                printExpr(b.right(), cp, true,  "right");
            }
            case Call c -> {
                emit(prefix, isLast, header(label, "Call callee=" + c.callee()));
                String cp = extend(prefix, isLast);
                List<Expr> args = c.args();
                for (int i = 0; i < args.size(); i++) {
                    printExpr(args.get(i), cp, i == args.size() - 1, null);
                }
            }

            case PhraseLit p -> {
                emit(prefix, isLast, header(label, "PhraseLit count=" + p.elements().size()));
                String cp = extend(prefix, isLast);
                List<Expr> elems = p.elements();
                for (int i = 0; i < elems.size(); i++) {
                    printExpr(elems.get(i), cp, i == elems.size() - 1, null);
                }
            }

            case EventLit e -> {
                String pitchInline = inlineExpr(e.pitch());
                if (pitchInline != null) {
                    emit(prefix, isLast, header(label,
                            "EventLit pitch=" + pitchInline + " dur=" + durationName(e.duration())));
                } else {
                    emit(prefix, isLast, header(label, "EventLit dur=" + durationName(e.duration())));
                    printExpr(e.pitch(), extend(prefix, isLast), true, "pitch");
                }
            }

            case Index ix -> {
                emit(prefix, isLast, header(label, "Index"));
                String cp = extend(prefix, isLast);
                printExpr(ix.target(), cp, false, "target");
                printExpr(ix.index(),  cp, true,  "index");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------

    private static void printVarDecl(VarDecl v, String prefix, boolean isLast) {
        emit(prefix, isLast, "VarDecl type=" + v.type() + " name=" + v.name());
        if (v.init() != null) {
            printExpr(v.init(), extend(prefix, isLast), true, null);
        }
    }

    private static void printPlay(Play p, String prefix, boolean isLast) {
        String form = p.parenthesized() ? "call" : "phrase";
        emit(prefix, isLast, "Play callee=" + p.callee() + " form=" + form);
        String cp = extend(prefix, isLast);
        List<Expr> args = p.args();
        for (int i = 0; i < args.size(); i++) {
            printExpr(args.get(i), cp, i == args.size() - 1, null);
        }
    }

    private static void printParam(Param p, String prefix, boolean isLast) {
        emit(prefix, isLast, "Param type=" + p.type() + " name=" + p.name());
    }

    /**
     * Render a Decl's "params then body" sequence. Params are {@link Param}
     * records (not in any sealed interface) and the body is a {@link Block}
     * ({@link Stmt}), so we route per element by type.
     */
    private static void emitMixed(List<Object> children, String prefix) {
        for (int i = 0; i < children.size(); i++) {
            Object c = children.get(i);
            boolean last = i == children.size() - 1;
            switch (c) {
                case Param p -> printParam(p, prefix, last);
                case Stmt s  -> printStmt(s, prefix, last);
                default -> throw new AssertionError(
                        "ASTPrinter: unexpected child kind " + c.getClass());
            }
        }
    }

    /** Prepend "label=" to {@code body}, if a label is present. */
    private static String header(String label, String body) {
        return label == null ? body : label + "=" + body;
    }

    /** Write one tree line. */
    private static void emit(String prefix, boolean isLast, String text) {
        System.out.println(prefix + (isLast ? "└── " : "├── ") + text);
    }

    /** Extend a prefix for the children of a node printed at (prefix, isLast). */
    private static String extend(String prefix, boolean isLast) {
        // 4 spaces of indentation per level. Non-last ancestors keep a
        // vertical bar in their column; last ancestors leave it blank.
        return prefix + (isLast ? "    " : "│   ");
    }

    /**
     * If {@code e} is a "simple" leaf expression, return its compact inline
     * form; otherwise null (caller should render it as a child node).
     */
    private static String inlineExpr(Expr e) {
        return switch (e) {
            case IntLit i   -> Integer.toString(i.value());
            case FloatLit f -> Double.toString(f.value());
            case BoolLit b  -> Boolean.toString(b.value());
            case NoteLit n  -> noteLitInline(n);
            case Ident id   -> id.name();
            case Call c     -> null;
            case Grouping g -> null;
            case Unary u    -> null;
            case BinOp b    -> null;
            case PhraseLit p -> null;
            case EventLit ev -> null;
            case Index ix   -> null;
        };
    }

    /** Compact rendering of a note literal: letter + optional accidental + octave. */
    private static String noteLitInline(NoteLit n) {
        String acc = switch (n.accidental()) {
            case NATURAL -> "";
            case SHARP   -> "#";
            case FLAT    -> "b";
        };
        return n.letter().name() + acc + n.octave();
    }

    /** Lower-case duration names, matching the grammar's surface syntax. */
    private static String durationName(Duration d) {
        return switch (d) {
            case WHOLE   -> "whole";
            case HALF    -> "half";
            case QUARTER -> "quarter";
            case EIGHTH  -> "eighth";
        };
    }

    /** Surface-syntax symbol for a binary operator (e.g. ADD -> "+"). */
    private static String binarySymbol(Expr.BinaryOp op) {
        return switch (op) {
            case ADD -> "+";
            case SUB -> "-";
            case MUL -> "*";
            case DIV -> "/";
            case EQ  -> "==";
            case NEQ -> "!=";
            case LT  -> "<";
            case LE  -> "<=";
            case GT  -> ">";
            case GE  -> ">=";
            case AND -> "&&";
            case OR  -> "||";
        };
    }

    /** Surface-syntax symbol for a unary operator (NEG -> "-", NOT -> "!"). */
    private static String unarySymbol(Expr.UnaryOp op) {
        return switch (op) {
            case NEG -> "-";
            case NOT -> "!";
        };
    }
}