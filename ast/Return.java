package ast;

public record Return(Expr value,
                     int line, int column) implements Stmt { }
