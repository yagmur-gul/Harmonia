package lexer;

// Whatever the lexer produces, we call it a Token
// type   = what kind (IDENT, PLUS, INT_LIT etc.)
// lexeme = the actual text in the source ("42", "+", "myVar")
// value  = real value for literals (Integer 42, Double 3.14, etc.)
// line/column = used in error messages
public final class Token {

    private final TokenType type;
    private final String    lexeme;
    private final Object    value;
    private final int       line;
    private final int       column;

    public Token(TokenType type, String lexeme, Object value, int line, int column) {
        this.type   = type;
        this.lexeme = lexeme;
        this.value  = value;
        this.line   = line;
        this.column = column;
    }
    
    // getters (could've used a record for shorter code but I wanted lexer package as classes)
    public TokenType getType() { return type; }
    public String get_Lexeme() { return lexeme; }
    public Object get_Value() { return value; }
    public int get_Line() { return line; }
    public int get_Column() { return column; }

    // for debug printing
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Token(").append(type.name());
        sb.append(", \"").append(lexeme).append('"');
        // IDENTs have null value, no need to print it
        if (value != null && type != TokenType.IDENT) {
            sb.append(", value=").append(value);
        }
        sb.append(", line=").append(line).append(", col=").append(column).append(')');
        return sb.toString();
    }
}