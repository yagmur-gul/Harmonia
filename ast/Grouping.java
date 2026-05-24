package ast;

public record Grouping(Expr inner, int line, int column) implements Expr { }
