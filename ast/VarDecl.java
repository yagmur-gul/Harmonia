package ast;

public record VarDecl(Type type, String name, Expr init,
                      int line, int column) implements Decl, Stmt { }
