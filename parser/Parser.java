package parser;

import ast.*;
import java.util.ArrayList;
import java.util.List;
import lexer.Token;
import lexer.TokenType;

// recursive descent parser
// one method per non-terminal
// grammar is in D1, I just followed it
public class Parser {

    private final List<Token> tokens;
    private int pos;   // index of the current token

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    // ---- helpers ----

    private Token peek() {
        return tokens.get(pos);
    }

    // sometimes I need to look 1-2 tokens ahead (e.g. is "note" a type or a stmt keyword?)
    private Token peekAt(int offset) {
        int idx = pos + offset;
        if (idx >= tokens.size()) {
            return tokens.get(tokens.size() - 1); // EOF
        }
        return tokens.get(idx);
    }

    private Token previous() {
        return tokens.get(pos - 1);
    }

    private boolean check(TokenType t) {
        return peek().getType() == t;
    }

    private Token advance() {
        if (!check(TokenType.EOF)) pos++;
        return previous();
    }

    // if current token matches one of the given types, consume it and return true
    private boolean match(TokenType... types) {
        for (TokenType t : types) {
            if (check(t)) {
                advance();
                return true;
            }
        }
        return false;
    }

    // token MUST be there; otherwise throw
    private Token expect(TokenType t) {
        if (check(t)) return advance();
        Token bad = peek();
        throw new ParseError("expected " + t + " but found '" + bad.get_Lexeme() + "'", bad.get_Line(), bad.get_Column());
    }

    private static Expr.BinaryOp toBinaryOp(TokenType type) {
        return switch (type) {
            case PLUS  -> Expr.BinaryOp.ADD;
            case MINUS -> Expr.BinaryOp.SUB;
            case STAR  -> Expr.BinaryOp.MUL;
            case SLASH -> Expr.BinaryOp.DIV;
            case EQ    -> Expr.BinaryOp.EQ;
            case NEQ   -> Expr.BinaryOp.NEQ;
            case LT    -> Expr.BinaryOp.LT;
            case LE    -> Expr.BinaryOp.LE;
            case GT    -> Expr.BinaryOp.GT;
            case GE    -> Expr.BinaryOp.GE;
            case AND   -> Expr.BinaryOp.AND;
            case OR    -> Expr.BinaryOp.OR;
            default    -> throw new IllegalStateException("Not a binary operator: " + type);
        };
    }

    private static Expr.UnaryOp toUnaryOp(TokenType type) {
        return switch (type) {
            case MINUS -> Expr.UnaryOp.NEG;
            case NOT   -> Expr.UnaryOp.NOT;
            default    -> throw new IllegalStateException("Not a unary operator: " + type);
        };
    }

    // does the current token start a <type> (int|float|bool|note|phrase|event)?
    private boolean isTypeStart() {
        return check(TokenType.INT)   || check(TokenType.FLOAT) ||
               check(TokenType.BOOL)  || check(TokenType.NOTE)  ||
               check(TokenType.PHRASE) || check(TokenType.EVENT);
    }

    // is the current token one of the duration keywords?
    private boolean isDurationStart() {
        return check(TokenType.WHOLE)   || check(TokenType.HALF) ||
               check(TokenType.QUARTER) || check(TokenType.EIGHTH);
    }

    // ---- entry point ----

    // <program> ::= { <top_level_decl> }
    public Program parseProgram() {
        List<TopLevel> items = new ArrayList<>();
        while (!check(TokenType.EOF)) {
            items.add(parseTopLevelDecl());
        }
        expect(TokenType.EOF);
        return new Program(items);
    }

    // for parsing a single expression (used by the old test main)
    public Expr parse() {
        Expr result = parseExpression();
        expect(TokenType.EOF);
        return result;
    }

    // ---- top-level ----

    // <top_level_decl> ::= <var_decl> | <melody_decl> | <func_decl> | <play_stmt>
    private TopLevel parseTopLevelDecl() {
        if (check(TokenType.MELODY)) return parseMelodyDecl();
        if (check(TokenType.FUNC))   return parseFuncDecl();
        if (check(TokenType.PLAY))   return parsePlayStmt();

        // var_decl starts with a type keyword
        if (isTypeStart()) {
            return parseVarDecl();
        }

        Token bad = peek();
        throw new ParseError(
            "expected top-level declaration (melody, func, play, or a type) but found '"
                + bad.get_Lexeme() + "'",
            bad.get_Line(), bad.get_Column()
        );
    }

    // <melody_decl> ::= "melody" IDENT "(" [ <param_list> ] ")" <block>
    private MelodyDecl parseMelodyDecl() {
        Token kw = expect(TokenType.MELODY);
        Token nameTok = expect(TokenType.IDENT);
        String name = nameTok.get_Lexeme();

        expect(TokenType.LPAREN);
        List<Param> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            params = parseParamList();
        }
        expect(TokenType.RPAREN);

        Block body = parseBlock();
        return new MelodyDecl(name, params, body, kw.get_Line(), kw.get_Column());
    }

    // <func_decl> ::= "func" <type> IDENT "(" [ <param_list> ] ")" <block>
    // only difference from melody is the return type
    private FuncDecl parseFuncDecl() {
        Token kw = expect(TokenType.FUNC);
        Type returnType = parseType();
        Token nameTok = expect(TokenType.IDENT);
        String name = nameTok.get_Lexeme();

        expect(TokenType.LPAREN);
        List<Param> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            params = parseParamList();
        }
        expect(TokenType.RPAREN);

        Block body = parseBlock();
        return new FuncDecl(returnType, name, params, body,
                            kw.get_Line(), kw.get_Column());
    }

    // <param_list> ::= <param> { "," <param> }
    private List<Param> parseParamList() {
        List<Param> params = new ArrayList<>();
        params.add(parseParam());
        while (match(TokenType.COMMA)) {
            params.add(parseParam());
        }
        return params;
    }

    // <param> ::= <type> IDENT
    private Param parseParam() {
        Token first = peek();
        Type t = parseType();
        Token nameTok = expect(TokenType.IDENT);
        return new Param(t, nameTok.get_Lexeme(),
                         first.get_Line(), first.get_Column());
    }

    // <type> ::= "int" | "float" | "bool" | "note" | "phrase" | "event"
    private Type parseType() {
        Token t = peek();
        if (match(TokenType.INT))    return Type.INT;
        if (match(TokenType.FLOAT))  return Type.FLOAT;
        if (match(TokenType.BOOL))   return Type.BOOL;
        if (match(TokenType.NOTE))   return Type.NOTE;
        if (match(TokenType.PHRASE)) return Type.PHRASE;
        if (match(TokenType.EVENT))  return Type.EVENT;

        throw new ParseError(
            "expected a type (int, float, bool, note, phrase, event) but found '"
                + t.get_Lexeme() + "'",
            t.get_Line(), t.get_Column()
        );
    }

    // ---- statements ----

    // <block> ::= "{" { <statement> } "}"
    private Block parseBlock() {
        Token open = expect(TokenType.LBRACE);
        List<Stmt> stmts = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            stmts.add(parseStatement());
        }
        expect(TokenType.RBRACE);
        return new Block(stmts, open.get_Line(), open.get_Column());
    }

    // statement dispatcher
    private Stmt parseStatement() {
        if (check(TokenType.LBRACE))    return parseBlock();

        if (check(TokenType.IF))        return parseIfStmt();
        if (check(TokenType.REPEAT))    return parseRepeatStmt();
        if (check(TokenType.RETURN))    return parseReturnStmt();
        if (check(TokenType.TEMPO))     return parseTempoStmt();
        if (check(TokenType.VOLUME))    return parseVolumeStmt();
        if (check(TokenType.PLAY))      return parsePlayStmt();
        if (check(TokenType.TRANSPOSE)) return parseTransposeStmt();

        // THIS IS THE PART THAT GAVE ME THE MOST TROUBLE
        // "note" is BOTH a type keyword AND a statement keyword
        //   note pitch1 = C4;        -> var_decl  (note IDENT '=')
        //   note pitch1;             -> var_decl  (note IDENT ';')
        //   note pitch1 quarter;     -> note_stmt (note IDENT DURATION)
        //   note C4 quarter;         -> note_stmt (note NOTE_LIT)
        // need to look 2 tokens ahead
        if (check(TokenType.NOTE)) {
            Token next = peekAt(1);
            if (next.getType() == TokenType.NOTE_LIT) {
                return parseNoteStmt();
            }
            if (next.getType() == TokenType.IDENT) {
                Token third = peekAt(2);
                TokenType tt = third.getType();
                if (tt == TokenType.WHOLE || tt == TokenType.HALF
                        || tt == TokenType.QUARTER || tt == TokenType.EIGHTH) {
                    return parseNoteStmt();
                }
                // = or ; is coming -> so it's a var decl
                return parseVarDecl();
            }
            Token bad = peek();
            throw new ParseError(
                "expected an identifier or a note literal after 'note' but found '"
                    + next.get_Lexeme() + "'",
                bad.get_Line(), bad.get_Column()
            );
        }

        // other types (int, float, bool, phrase) -> definitely a var_decl
        if (isTypeStart()) {
            return parseVarDecl();
        }

        // starts with IDENT and next token is '=' -> assignment
        if (check(TokenType.IDENT) && peekAt(1).getType() == TokenType.ASSIGN) {
            return parseAssignment();
        }

        // anything else is an error - expression statements are not allowed
        // (I wrote this in D1 4.1.3: things like "2 + 3;" are rejected)
        Token bad = peek();
        throw new ParseError(
            "expected statement but found '" + bad.get_Lexeme() + "'",
            bad.get_Line(), bad.get_Column()
        );
    }

    // <var_decl> ::= <type> IDENT [ "=" <expression> ] ";"
    private VarDecl parseVarDecl() {
        Token first = peek();
        Type type = parseType();
        Token nameTok = expect(TokenType.IDENT);
        Expr init = null;
        if (match(TokenType.ASSIGN)) {
            init = parseExpression();
        }
        expect(TokenType.SEMI);
        return new VarDecl(type, nameTok.get_Lexeme(), init,
                           first.get_Line(), first.get_Column());
    }

    // <assignment> ::= IDENT "=" <expression> ";"
    private Assign parseAssignment() {
        Token nameTok = expect(TokenType.IDENT);
        expect(TokenType.ASSIGN);
        Expr value = parseExpression();
        expect(TokenType.SEMI);
        return new Assign(nameTok.get_Lexeme(), value,
                          nameTok.get_Line(), nameTok.get_Column());
    }

    // <if_stmt> ::= "if" <expression> <block> [ "else" ( <if_stmt> | <block> ) ]
    // no dangling-else problem since both branches MUST be blocks (D1 4.3.2)
    private If parseIfStmt() {
        Token kw = expect(TokenType.IF);
        Expr cond = parseExpression();
        Block thenBlock = parseBlock();
        Stmt elseBranch = null;
        if (match(TokenType.ELSE)) {
            // recursive call for else-if chains
            if (check(TokenType.IF)) {
                elseBranch = parseIfStmt();
            } else {
                elseBranch = parseBlock();
            }
        }
        return new If(cond, thenBlock, elseBranch,
                      kw.get_Line(), kw.get_Column());
    }

    // <repeat_stmt> ::= "repeat" <expression> "times" <block>
    private Repeat parseRepeatStmt() {
        Token kw = expect(TokenType.REPEAT);
        Expr count = parseExpression();
        expect(TokenType.TIMES);
        Block body = parseBlock();
        return new Repeat(count, body, kw.get_Line(), kw.get_Column());
    }

    // <return_stmt> ::= "return" [ <expression> ] ";"
    private Return parseReturnStmt() {
        Token kw = expect(TokenType.RETURN);
        Expr value = null;
        if (!check(TokenType.SEMI)) {
            value = parseExpression();
        }
        expect(TokenType.SEMI);
        return new Return(value, kw.get_Line(), kw.get_Column());
    }

    // <tempo_stmt> ::= "tempo" <expression> ";"
    private Tempo parseTempoStmt() {
        Token kw = expect(TokenType.TEMPO);
        Expr value = parseExpression();
        expect(TokenType.SEMI);
        return new Tempo(value, kw.get_Line(), kw.get_Column());
    }

    // <volume_stmt> ::= "volume" <expression> ";"
    private Volume parseVolumeStmt() {
        Token kw = expect(TokenType.VOLUME);
        Expr value = parseExpression();
        expect(TokenType.SEMI);
        return new Volume(value, kw.get_Line(), kw.get_Column());
    }

    // <note_stmt> ::= "note" <pitch> <duration> ";"
    // <pitch>     ::= NOTE_LIT | IDENT
    private NoteStmt parseNoteStmt() {
        Token kw = expect(TokenType.NOTE);
        Expr pitch = parsePitch();
        Duration duration = parseDuration();
        expect(TokenType.SEMI);
        return new NoteStmt(pitch, duration, kw.get_Line(), kw.get_Column());
    }

    // <pitch> ::= NOTE_LIT | IDENT
    // Shared by <note_stmt> and the <event> literals inside a phrase literal.
    // A NOTE_LIT (C4, D#5, ...) becomes a NoteLit; an IDENT (a user variable)
    // becomes an Ident.
    private Expr parsePitch() {
        Token pTok = peek();
        if (match(TokenType.NOTE_LIT)) {
            return noteLitFrom(previous());
        }
        if (match(TokenType.IDENT)) {
            Token tok = previous();
            return new Ident(tok.get_Lexeme(), tok.get_Line(), tok.get_Column());
        }
        throw new ParseError(
            "expected a pitch (NOTE_LIT or IDENT) but found '"
                + pTok.get_Lexeme() + "'",
            pTok.get_Line(), pTok.get_Column()
        );
    }

    // Decompose a NOTE_LIT token (e.g. "D#5") into a NoteLit AST node:
    // letter + optional accidental (#/b) + octave. Single source of truth for
    // both <primary> and <pitch>.
    private NoteLit noteLitFrom(Token tok) {
        String text = (String) tok.get_Value();
        NoteName letter = NoteName.valueOf(String.valueOf(text.charAt(0)));
        Accidental accidental;
        int octaveStart;
        if (text.length() > 1 && text.charAt(1) == '#') {
            accidental = Accidental.SHARP;
            octaveStart = 2;
        } else if (text.length() > 1 && text.charAt(1) == 'b') {
            accidental = Accidental.FLAT;
            octaveStart = 2;
        } else {
            accidental = Accidental.NATURAL;
            octaveStart = 1;
        }
        int octave = Integer.parseInt(text.substring(octaveStart));
        return new NoteLit(letter, accidental, octave,
                           tok.get_Line(), tok.get_Column());
    }

    // <phrase_lit> ::= "[" [ <event> { "," <event> } ] "]"
    // The opening "[" has already been consumed; `open` is that token.
    // Empty literal "[]" is allowed. Each element is an <event>.
    private PhraseLit parsePhraseLit(Token open) {
        List<Expr> elements = new ArrayList<>();
        if (!check(TokenType.RBRACKET)) {
            elements.add(parseEventLit());
            while (match(TokenType.COMMA)) {
                elements.add(parseEventLit());
            }
        }
        expect(TokenType.RBRACKET);
        return new PhraseLit(elements, open.get_Line(), open.get_Column());
    }

    // <event> ::= <pitch> <duration>
    // The same shape as the body of a <note_stmt> (without the "note" keyword
    // and trailing ";"). Indexing a phrase yields one of these, so the
    // duration is preserved.
    private EventLit parseEventLit() {
        Token first = peek();
        Expr pitch = parsePitch();
        Duration duration = parseDuration();
        return new EventLit(pitch, duration, first.get_Line(), first.get_Column());
    }

    private Duration parseDuration() {
        Token t = peek();
        if (match(TokenType.WHOLE))   return Duration.WHOLE;
        if (match(TokenType.HALF))    return Duration.HALF;
        if (match(TokenType.QUARTER)) return Duration.QUARTER;
        if (match(TokenType.EIGHTH))  return Duration.EIGHTH;
        throw new ParseError(
            "expected a duration (whole, half, quarter, eighth) but found '"
                + t.get_Lexeme() + "'",
            t.get_Line(), t.get_Column()
        );
    }

    // <play_stmt> ::= "play" IDENT "(" [ <arg_list> ] ")" ";"   (* melody / call form *)
    //              |  "play" IDENT ";"                          (* phrase form, no parens *)
    // The parser accepts both; the type checker decides which is legal (a call
    // form names a melody, the bare form names a phrase variable). The two are
    // told apart in the AST by the `parenthesized` flag on Play.
    private Play parsePlayStmt() {
        Token kw = expect(TokenType.PLAY);
        Token nameTok = expect(TokenType.IDENT);

        if (match(TokenType.LPAREN)) {
            // call form: play foo(a, b);  (arg list may be empty: play foo();)
            List<Expr> args = parseArgList();
            expect(TokenType.RPAREN);
            expect(TokenType.SEMI);
            return new Play(nameTok.get_Lexeme(), args, true,
                            kw.get_Line(), kw.get_Column());
        }

        // phrase form: play intro;  -> no arguments, not parenthesized
        expect(TokenType.SEMI);
        return new Play(nameTok.get_Lexeme(), new ArrayList<>(), false,
                        kw.get_Line(), kw.get_Column());
    }

    // <transpose_stmt> ::= "transpose" <expression> <block>
    // note: NO ';' at the end since it ends with a block
    private Transpose parseTransposeStmt() {
        Token kw = expect(TokenType.TRANSPOSE);
        Expr amount = parseExpression();
        Block body = parseBlock();
        return new Transpose(amount, body, kw.get_Line(), kw.get_Column());
    }

    // ---- expressions ----
    // precedence: low -> high
    //   or > and > relational > additive > multiplicative > unary > primary
    // each level uses { ... } loop so it becomes left-associative (D1 4.3.2)

    private Expr parseExpression() {
        return parseOr();
    }

    private Expr parseOr() {
        Expr left = parseAnd();
        while (match(TokenType.OR)) {
            Token opToken = previous();
            Expr right = parseAnd();
            left = new BinOp(left, Expr.BinaryOp.OR, right,
                             opToken.get_Line(), opToken.get_Column());
        }
        return left;
    }

    private Expr parseAnd() {
        Expr left = parseRelational();
        while (match(TokenType.AND)) {
            Token opToken = previous();
            Expr right = parseRelational();
            left = new BinOp(left, Expr.BinaryOp.AND, right,
                             opToken.get_Line(), opToken.get_Column());
        }
        return left;
    }

    // <relational> ::= <additive> [ <rel_op> <additive> ]
    // here it's an IF, not a while; because relational chaining is not allowed (1 < x < 10 is invalid)
    private Expr parseRelational() {
        Expr left = parseAdditive();
        if (match(TokenType.EQ, TokenType.NEQ,
                  TokenType.LT, TokenType.LE,
                  TokenType.GT, TokenType.GE)) {
            Token opToken = previous();
            Expr right = parseAdditive();
            return new BinOp(left, toBinaryOp(opToken.getType()), right,
                             opToken.get_Line(), opToken.get_Column());
        }
        return left;
    }

    // <additive> ::= <multiplicative> { ("+" | "-") <multiplicative> }
    private Expr parseAdditive() {
        Expr left = parseMultiplicative();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token opToken = previous();
            Expr right = parseMultiplicative();
            left = new BinOp(left, toBinaryOp(opToken.getType()), right,
                             opToken.get_Line(), opToken.get_Column());
        }
        return left;
    }

    // <multiplicative> ::= <unary> { ("*" | "/") <unary> }
    private Expr parseMultiplicative() {
        Expr left = parseUnary();
        while (match(TokenType.STAR, TokenType.SLASH)) {
            Token opToken = previous();
            Expr right = parseUnary();
            left = new BinOp(left, toBinaryOp(opToken.getType()), right,
                             opToken.get_Line(), opToken.get_Column());
        }
        return left;
    }

    // <unary> ::= ("-" | "!") <unary> | <postfix>
    // right-associative: --x = -(-x)  --> recursive call
    private Expr parseUnary() {
        if (match(TokenType.MINUS, TokenType.NOT)) {
            Token opToken = previous();
            Expr operand = parseUnary();
            return new Unary(toUnaryOp(opToken.getType()), operand,
                             opToken.get_Line(), opToken.get_Column());
        }
        return parsePostfix();
    }

    // <postfix> ::= <primary> { "[" <expression> "]" }
    // Postfix indexing binds tighter than unary (so -a[0] is -(a[0])) and is
    // left-associative (a[0][1] is (a[0])[1]). Purely additive: it sits between
    // <unary> and <primary> and leaves every other precedence level untouched.
    private Expr parsePostfix() {
        Expr expr = parsePrimary();
        while (match(TokenType.LBRACKET)) {
            Token bracket = previous();
            Expr index = parseExpression();
            expect(TokenType.RBRACKET);
            expr = new Index(expr, index, bracket.get_Line(), bracket.get_Column());
        }
        return expr;
    }

    // <primary> ::= INT_LIT | FLOAT_LIT | BOOL_LIT | NOTE_LIT
    //             | IDENT [ "(" [ <arg_list> ] ")" ]
    //             | "(" <expression> ")"
    //             | <phrase_lit>
    private Expr parsePrimary() {
        Token t = peek();

        if (match(TokenType.INT_LIT)) {
            Token tok = previous();
            int v = (Integer) tok.get_Value();
            return new IntLit(v, tok.get_Line(), tok.get_Column());
        }

        if (match(TokenType.FLOAT_LIT)) {
            Token tok = previous();
            double v = (Double) tok.get_Value();
            return new FloatLit(v, tok.get_Line(), tok.get_Column());
        }

        if (match(TokenType.BOOL_LIT)) {
            Token tok = previous();
            boolean v = (Boolean) tok.get_Value();
            return new BoolLit(v, tok.get_Line(), tok.get_Column());
        }

        // NOTE_LIT: letter + (#|b)? + octave
        if (match(TokenType.NOTE_LIT)) {
            return noteLitFrom(previous());
        }

        // IDENT [ "(" [ <arg_list> ] ")" ]
        if (match(TokenType.IDENT)) {
            Token tok = previous();
            String name = tok.get_Lexeme();
            int line = tok.get_Line();
            int col  = tok.get_Column();

            // single-token lookahead: if '(' follows, it's a Call; otherwise just an Ident
            if (match(TokenType.LPAREN)) {
                List<Expr> args = parseArgList(); // does NOT consume the ')'
                expect(TokenType.RPAREN);         // ')' is expected here
                return new Call(name, args, line, col);
            }

            return new Ident(name, line, col);
        }

        // "(" <expression> ")"
        if (match(TokenType.LPAREN)) {
            Token tok = previous();
            Expr inner = parseExpression();
            // Improved diagnostic: "(y = 5)" -- after parsing 'y' as an Ident
            // we expect ')', but if we see '=' the user has tried to use an
            // assignment as an expression. In Harmonia <assignment> is a
            // statement, not an <expression> (see D1 4.7), so report that
            // explicitly instead of the generic "expected RPAREN" message.
            if (check(TokenType.ASSIGN)) {
                Token bad = peek();
                throw new ParseError(
                    "assignment is a statement, not an expression "
                        + "(it cannot appear inside parentheses)",
                    bad.get_Line(), bad.get_Column()
                );
            }
            expect(TokenType.RPAREN);
            return new Grouping(inner, tok.get_Line(), tok.get_Column());
        }

        // "[" [ <event> { "," <event> } ] "]"   -- phrase array literal
        if (match(TokenType.LBRACKET)) {
            return parsePhraseLit(previous());
        }

        throw new ParseError(
            "expected expression but found '" + t.get_Lexeme() + "'",
            t.get_Line(), t.get_Column()
        );
    }

    // <arg_list> ::= [ <expression> { "," <expression> } ]
    // can be empty: for calls like f()
    private List<Expr> parseArgList() {
        List<Expr> args = new ArrayList<>();

        if (check(TokenType.RPAREN)) {
            return args;
        }

        args.add(parseExpression());

        while (match(TokenType.COMMA)) {
            args.add(parseExpression());
        }

        return args;
    }
}