package ast;

public sealed interface Expr
        permits IntLit, FloatLit, BoolLit, NoteLit, Ident, Call, Grouping, Unary, BinOp,
                PhraseLit, EventLit, Index {

    enum BinaryOp {
        ADD, SUB, MUL, DIV,
        EQ, NEQ, LT, LE, GT, GE,
        AND, OR
    }

    enum UnaryOp {
        NEG, NOT
    }
}