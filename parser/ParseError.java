package parser;

// parser error: needs to carry where it happened so we can tell the user
// (handout asks for "useful error message including line number")
public class ParseError extends RuntimeException {
    public final int line;
    public final int column;

    public ParseError(String message, int line, int column) {
        super("Parse error at line " + line + ", col " + column + ": " + message);
        this.line = line;
        this.column = column;
    }
}