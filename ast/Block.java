package ast;

import java.util.List;

public record Block(List<Stmt> statements,
                    int line, int column) implements Stmt { }
