package ast;

public record Repeat(Expr count, Block body,
                     int line, int column) implements Stmt { }
