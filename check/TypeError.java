package check;

// A type error found during static checking, before any program runs.
// Mirrors parser.ParseError: it carries the line/column of the offending
// construct so the message can point at where the problem is, exactly as
// the handout asks ("useful error message including line number").
public class TypeError extends RuntimeException {
    public final int line;
    public final int column;

    public TypeError(String message, int line, int column) {
        super("Type error at line " + line + ", col " + column + ": " + message);
        this.line = line;
        this.column = column;
    }
}