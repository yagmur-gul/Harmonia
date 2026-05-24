package ast;

public record Ident(String name, int line, int column) implements Expr { }
