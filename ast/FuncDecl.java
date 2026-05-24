package ast;

import java.util.List;

public record FuncDecl(Type returnType, String name, List<Param> params, Block body,
                       int line, int column) implements Decl { }
