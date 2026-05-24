package ast;

public sealed interface Stmt permits VarDecl, Assign, If, Repeat, Return,
                                      Tempo, Volume, NoteStmt, Play, Transpose, Block {
}
