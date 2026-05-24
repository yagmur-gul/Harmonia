package ast;

public record NoteLit(NoteName letter, Accidental accidental, int octave,
                      int line, int column) implements Expr { }
