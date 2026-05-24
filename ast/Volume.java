package ast;

public record Volume(Expr value,
                     int line, int column) implements Stmt { }
