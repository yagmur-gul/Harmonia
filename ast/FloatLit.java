package ast;

public record FloatLit(double value, int line, int column) implements Expr { }
