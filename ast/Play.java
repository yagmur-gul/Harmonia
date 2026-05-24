package ast;

import java.util.List;

// A play statement. Two surface forms share this node:
//   play foo(args);  -> parenthesized = true   (melody / call form)
//   play intro;      -> parenthesized = false  (phrase form, args is empty)
public record Play(String callee, List<Expr> args, boolean parenthesized,
                   int line, int column) implements TopLevel, Stmt { }