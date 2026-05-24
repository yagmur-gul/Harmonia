package ast;

// An event literal written inside a phrase array literal: <pitch> <duration>e.g. "C4 quarter"
public record EventLit(Expr pitch, Duration duration,
                       int line, int column) implements Expr { }