package interp;

/* An error raised while the program runs — after type checking has passed. */
public class RuntimeError extends RuntimeException {
    public final int line;
    public final int column;

    public RuntimeError(String message, int line, int column) {
        super("Runtime error at line " + line + ", col " + column + ": " + message);
        this.line = line;
        this.column = column;
    }
}