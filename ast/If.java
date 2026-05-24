package ast;

public record If(Expr cond, Block thenBlock, Stmt elseBranch,
                 int line, int column) implements Stmt { }
