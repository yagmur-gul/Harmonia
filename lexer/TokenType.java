package lexer;
/* The full set of token kinds for Harmonia */
public enum TokenType {

    // ---- literals ----
    INT_LIT,
    FLOAT_LIT,
    BOOL_LIT,      // true | false
    NOTE_LIT,      // e.g. C4, D#5, Bb3
    IDENT,

    // ---- type keywords ----
    INT,
    FLOAT,
    BOOL,
    NOTE,          // also a statement keyword; parser disambiguates by context
    PHRASE,
    EVENT,         // element type of a phrase: a (pitch, duration) pair

    // ---- declaration / control keywords ----
    MELODY,
    FUNC,
    RETURN,
    IF,
    ELSE,
    REPEAT,
    TIMES,
    PLAY,
    TRANSPOSE,
    TEMPO,
    VOLUME,

    // ---- duration keywords ----
    WHOLE,
    HALF,
    QUARTER,
    EIGHTH,

    // ---- operators ----
    PLUS,          // +
    MINUS,         // -
    STAR,          // *
    SLASH,         // /
    ASSIGN,        // =

    EQ,            // ==
    NEQ,           // !=
    LT,            // <
    LE,            // <=
    GT,            // >
    GE,            // >=

    AND,           // &&
    OR,            // ||
    NOT,           // !

    // ---- separators ----
    LPAREN,        // (
    RPAREN,        // )
    LBRACE,        // {
    RBRACE,        // }
    LBRACKET,      // [
    RBRACKET,      // ]
    COMMA,         // ,
    SEMI,          // ;

    // ---- end-of-file ----
    EOF
}