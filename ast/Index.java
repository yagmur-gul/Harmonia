package ast;

// Postfix indexing: <target> "[" <index> "]"
public record Index(Expr target, Expr index,
                    int line, int column) implements Expr { }