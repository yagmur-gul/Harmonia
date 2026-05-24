package ast;

public sealed interface Decl extends TopLevel permits MelodyDecl, FuncDecl, VarDecl {
}
