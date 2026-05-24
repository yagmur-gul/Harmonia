package ast;

public record NoteStmt(Expr pitch, Duration duration,
                       int line, int column) implements Stmt { }
