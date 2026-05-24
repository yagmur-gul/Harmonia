package ast;

import java.util.List;

public record MelodyDecl(String name, List<Param> params, Block body,
                         int line, int column) implements Decl { }
