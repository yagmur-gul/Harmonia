package ast;

public record Unary(Expr.UnaryOp op, Expr operand,
                    int line, int column) implements Expr { }
