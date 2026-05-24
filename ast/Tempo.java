package ast;

public record Tempo(Expr value,
                    int line, int column) implements Stmt { }
