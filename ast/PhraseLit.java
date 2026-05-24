package ast;

import java.util.List;

// A phrase array literal: "[" [ <event> { "," <event> } ] "]"
public record PhraseLit(List<Expr> elements,
                        int line, int column) implements Expr { }