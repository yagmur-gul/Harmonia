package ast;

public record Transpose(Expr amount, Block body,
                        int line, int column) implements Stmt { }
