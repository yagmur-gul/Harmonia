package ast;

public record BoolLit(boolean value, int line, int column) implements Expr { }
