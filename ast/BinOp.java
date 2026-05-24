package ast;

public record BinOp(Expr left, Expr.BinaryOp op, Expr right,
                    int line, int column) implements Expr { }
