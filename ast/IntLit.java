package ast;

public record IntLit(int value, int line, int column) implements Expr { }
