package lexer;

import java.util.ArrayList;
import java.util.List;

// Scanner (lexer) for Harmonia
// source code -> list of Tokens
// maximal-munch: picks the longest matching lexeme (e.g. "C4" -> NOTE_LIT, lone "C" -> IDENT)
public class Lexer {

    private final String source;
    private int pos;       // current character
    private int start;     // where the current token started
    private int line;
    private int column;
    private final List<Token> tokens;

    public Lexer(String source) {
        this.source = source;
        this.pos = 0;
        this.start = 0;
        this.line = 1;
        this.column = 1;
        this.tokens = new ArrayList<>();
    }

    // is a word an IDENT or a keyword? listed all of them here
    // (matches the keyword list in D1 4.2.1 exactly)
    private TokenType identifierType(String text) {
        return switch (text) {
            case "melody" -> TokenType.MELODY;
            case "func" -> TokenType.FUNC;
            case "return" -> TokenType.RETURN;
            case "if" -> TokenType.IF;
            case "else" -> TokenType.ELSE;
            case "repeat" -> TokenType.REPEAT;
            case "times" -> TokenType.TIMES;
            case "play" -> TokenType.PLAY;
            case "transpose" -> TokenType.TRANSPOSE;
            case "tempo" -> TokenType.TEMPO;
            case "volume" -> TokenType.VOLUME;
            case "int" -> TokenType.INT;
            case "float" -> TokenType.FLOAT;
            case "bool" -> TokenType.BOOL;
            case "note" -> TokenType.NOTE;
            case "phrase" -> TokenType.PHRASE;
            case "event" -> TokenType.EVENT;
            case "true" -> TokenType.BOOL_LIT;
            case "false" -> TokenType.BOOL_LIT;
            case "whole" -> TokenType.WHOLE;
            case "half" -> TokenType.HALF;
            case "quarter" -> TokenType.QUARTER;
            case "eighth" -> TokenType.EIGHTH;
            default -> TokenType.IDENT;
        };
    }

    private boolean isAtEnd() {  return pos >= source.length(); }
    private char peek() { return isAtEnd() ? '\0' : source.charAt(pos); }

    // peek 1 character ahead (needed for 2-char operators like ==, !=)
    private char peekNext() {
        if (pos + 1 >= source.length()) {
            return '\0';
        }
        return source.charAt(pos + 1);
    }

    // grab current char, advance, update line/column info
    private char advance() {
        char c = peek();
        pos++;
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;       
        }
        return c;
    }

    // if expected char is here, consume it; else false
    // needed for ==, !=, <=, >=, &&, ||
    private boolean match(char expected) {
        if (isAtEnd() || peek() != expected) return false;
        advance();
        return true;
    }

   private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c) || c == '_';
    }

    // is it a note letter (A-G)?
    // if I don't keep these separate, C, D etc. get caught as IDENTs
    private static boolean isNoteLetter(char c) {
        return c >= 'A' && c <= 'G';
    }

    // Called after we've just consumed an A-G letter. Decides whether the
    // following characters form a real NOTE_LIT tail: optional '#'|'b' then
    // at least one digit. The accidental-only case still needs the digit
    // after it, otherwise "Bar" would qualify on the 'b'. We also guard
    // against alphanumeric continuations like "C4thing" -- if a digit run
    // is followed by another letter/underscore, this is an identifier, not
    // a note.
    private boolean looksLikeNote() {
        int i = pos;
        if (i < source.length() && (source.charAt(i) == '#' || source.charAt(i) == 'b')) {
            i++;
        }
        // need at least one digit
        if (i >= source.length() || !isDigit(source.charAt(i))) {
            return false;
        }
        // consume the digit run, then make sure we don't bleed into an identifier
        while (i < source.length() && isDigit(source.charAt(i))) {
            i++;
        }
        if (i < source.length() && (isAlpha(source.charAt(i)) || source.charAt(i) == '_')) {
            return false;
        }
        return true;
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object value) {
        String lexeme = source.substring(start, pos);
        // subtract lexeme.length() because column has already advanced past the token
        tokens.add(new Token(type, lexeme, value, line, column - lexeme.length()));
    }

    // main loop: consume one token, then another, ... until EOF
    public List<Token> tokenize() {
        while (!isAtEnd()) {
            start = pos;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line, column));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            // single-char operators / separators
            // '(' can also start a block comment "(* ... *)"
            case '(' -> {
                if (match('*')) {
                    // block comment: skip everything until the matching "*)"
                    skipBlockComment();
                } else {
                    addToken(TokenType.LPAREN);
                }
            }
            case ')' -> addToken(TokenType.RPAREN);
            case '{' -> addToken(TokenType.LBRACE);
            case '}' -> addToken(TokenType.RBRACE);
            case '[' -> addToken(TokenType.LBRACKET);
            case ']' -> addToken(TokenType.RBRACKET);
            case ',' -> addToken(TokenType.COMMA);
            case ';' -> addToken(TokenType.SEMI);
            case '+' -> addToken(TokenType.PLUS);
            case '-' -> addToken(TokenType.MINUS);
            case '*' -> addToken(TokenType.STAR);

            // possibly 2-char: =/==  !/!=  </<=  >/>=
            case '=' -> addToken(match('=') ? TokenType.EQ : TokenType.ASSIGN);
            case '!' -> addToken(match('=') ? TokenType.NEQ : TokenType.NOT);
            case '<' -> addToken(match('=') ? TokenType.LE : TokenType.LT);
            case '>' -> addToken(match('=') ? TokenType.GE : TokenType.GT);

            // && and || -> a single '&' or '|' is INVALID
            case '&' -> {
                if (match('&')) {
                    addToken(TokenType.AND);
                } else {
                    throw new RuntimeException("Unexpected character '&' at line " + line + ", column " + column);
                }
            }
            case '|' -> {
                if (match('|')) {
                    addToken(TokenType.OR);
                } else {
                    throw new RuntimeException("Unexpected character '|' at line " + line + ", column " + column);
                }
            }

            // / is either division or the start of a comment (//)
            case '/' -> {
                if (match('/')) {
                    // skip until end of line
                    while (peek() != '\n' && !isAtEnd()) {
                        advance();
                    }
                } else {
                    addToken(TokenType.SLASH);
                }
            }

            // whitespace -> ignore
            case ' ', '\r', '\t' -> {
            }
            case '\n' -> {
                // newline counting is done in advance(), don't emit a token here
            }

            default -> {
                if (isDigit(c)) {
                    scanNumber();
                } else if (isNoteLetter(c) && looksLikeNote()) {
                    // [A-G] followed by an optional accidental and at least one digit -> NOTE_LIT.
                    // Anything else starting with A-G (e.g. "Arpeggio", "Bar", lone "C") falls
                    // through to scanIdent so identifiers don't get sliced in half.
                    scanNote();
                } else if (isAlpha(c)) {
                    scanIdent();
                } else {
                    throw new RuntimeException("Unexpected character '" + c + "' at line " + line + ", column " + column);
                }
            }
        }
    }

    // Block comment: "(* ... *)". Non-nested (an inner "(*" is just text).
    // Called *after* the opening "(*" has been consumed; consumes through the
    // matching "*)" and discards everything in between. Newlines inside the
    // comment update line/column normally via advance().
    private void skipBlockComment() {
        int startLine = line;
        int startCol  = column - 2; // column already past the "(*"
        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == ')') {
                advance(); // consume '*'
                advance(); // consume ')'
                return;
            }
            advance();
        }
        // EOF without "*)"
        throw new RuntimeException(
            "Unterminated block comment starting at line " + startLine + ", column " + startCol);
    }
    
    // number: int or float. 3.14 -> float, 42 -> int
    private void scanNumber() {
        while (isDigit(peek())) advance();

        // is there a fractional part? (need both '.' AND a digit after it)
        if (peek() == '.' && isDigit(peekNext())) {
            advance(); // consume the '.'
            while (isDigit(peek())) advance();
            addToken(TokenType.FLOAT_LIT, Double.valueOf(source.substring(start, pos)));
        } else {
            addToken(TokenType.INT_LIT, Integer.valueOf(source.substring(start, pos)));
        }
    }

    // identifier or keyword
    private void scanIdent() {
        while (isAlphaNumeric(peek())) advance();
        String text = source.substring(start, pos);
        TokenType type = identifierType(text);
        // true/false are special: marked as BOOL_LIT, with the actual bool stored as value
        if (type == TokenType.BOOL_LIT) {
            boolean value = text.equals("true");
            addToken(type, value);
        } else {
            addToken(type);
        }
    }

    // note literal: [A-G] ('#'|'b')? [0-9]+
    // examples: C4, D#5, Bb3
    // Precondition: caller has verified via looksLikeNote() that the tail is
    // valid, so we can blindly consume the optional accidental and digit run.
    private void scanNote() {
        if (peek() == '#' || peek() == 'b') {
            advance();
        }
        while (isDigit(peek())) {
            advance();
         }
        addToken(TokenType.NOTE_LIT, source.substring(start, pos));
    }
}