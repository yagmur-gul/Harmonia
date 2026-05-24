package ast;

public record Assign(String name, Expr value, int line, int column) implements Stmt { }
