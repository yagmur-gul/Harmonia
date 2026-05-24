================================================================
Harmonia — DSL for Music Composition
CSE 341 · Part 2 · Yağmur GÜL · 220104004014
================================================================

LANGUAGE
  Harmonia is a domain-specific language for composing and
  playing musical pieces. Part 1 delivered the lexer + parser
  and an AST pretty-printer (--dump-ast). Part 2 adds a static
  type checker (--check) and a tree-walking interpreter that
  executes a program and produces music through three outputs:
  a console performance trace, live audio via the JVM's built-in
  MIDI synthesizer, and an optional Standard MIDI File (.mid).

REQUIREMENTS
  Java 21 or later (uses pattern matching in switch over
  sealed interfaces, a Java 21 language feature).
  No external libraries or build tools required.
  Live audio uses javax.sound.midi (part of the standard JDK).

PROJECT STRUCTURE
  220104004014_D2_P2/
  |-- Main.java            CLI entry point
  |-- lexer/
  |   |-- Lexer.java       Token scanner (// and (* *) comments)
  |   |-- Token.java       Token data class
  |   `-- TokenType.java   Token type enum
  |-- parser/
  |   |-- Parser.java      Recursive-descent parser
  |   `-- ParseError.java  Parse exception
  |-- ast/
  |   |-- Program.java     Root AST node
  |   |-- Decl.java        Declaration interface
  |   |-- Stmt.java        Statement interface
  |   |-- Expr.java        Expression interface
  |   `-- ...              (27 AST node classes as sealed
  |                         records, plus 4 sealed interfaces
  |                         and 4 enums)
  |-- check/
  |   |-- TypeChecker.java Static type checker (two-pass)
  |   |-- TypeEnv.java     Compile-time scopes + callable table
  |   `-- TypeError.java   Type-error exception (with line/col)
  |-- interp/
  |   |-- Interpreter.java Tree-walking evaluator
  |   |-- Environment.java Runtime scopes (parent-pointer chain)
  |   |-- Values.java      Runtime value classes (note/event/phrase)
  |   |-- Pitch.java       NoteLit <-> MIDI number (C4 = 60)
  |   |-- NotePlayer.java  Output interface (the event sink)
  |   |-- ConsolePlayer.java   Prints the performance trace
  |   |-- MidiPlayer.java      Live audio via javax.sound.midi
  |   |-- MidiFilePlayer.java  Writes a .mid file
  |   |-- RuntimeError.java    Runtime-error exception (line/col)
  |   `-- ReturnSignal.java    Control-flow signal for 'return'
  `-- util/
      `-- ASTPrinter.java  Indented tree pretty-printer

================================================================
COMPILE
================================================================

From inside the 220104004014_D2_P2/ directory:

  Windows (Command Prompt):
    javac -d out lexer\*.java ast\*.java parser\*.java check\*.java util\*.java interp\*.java Main.java

  macOS / Linux:
    javac -d out lexer/*.java ast/*.java parser/*.java check/*.java util/*.java interp/*.java Main.java

This compiles all sources into an "out/" directory.

================================================================
USAGE
================================================================

The CLI entry point is Main. After compiling:

  java -cp out Main <source-file> [flags]

Flags:
  --dump-ast    Parse the file and print the AST as an indented
                tree to stdout, then exit. (No type check / run.)
  --check       Parse and type-check only. Prints "OK: no type
                errors." on success, or a type error and exits
                non-zero on failure. Does not run the program.
  --midi[=file] Also write a Standard MIDI File. With "=file" it
                writes to that path; bare --midi writes a default
                file name. Without this flag, no .mid is written.
  --no-audio    Skip live audio playback (still prints the trace
                and still writes the .mid file if --midi is given).

With no mode flag, Main parses, type-checks, then runs the
program: it prints the console trace, plays live audio, and
(if --midi was passed) writes the .mid file.

PIPELINE
  Every run is parse -> type-check -> interpret. The interpreter
  relies on the type checker having passed, so a program that
  fails --check will not be run.

ERRORS
  Lexer errors:   Lexer error: <description> at line L, column C
  Parse errors:   Parse error at line L, col C: <description>
  Type errors:    Type error at line L, col C: <description>
  Runtime errors: Runtime error at line L, col C: <description>
  In every case the process exits with a non-zero status.
  Lexer errors are raised for an unexpected character and for an
  unterminated block comment (reported at its starting position).
  Runtime errors are raised for division by zero, phrase index
  out of range, and the domain-specific case of a transpose that
  pushes a note outside the valid MIDI range 0-127.

Examples:
  java -cp out Main tests/V1.harm --dump-ast
  java -cp out Main tests/V1.harm --check
  java -cp out Main tests/V1.harm
  java -cp out Main tests/V1.harm --midi=V1.mid --no-audio

================================================================
RUNNING THE D3 TEST CASES
================================================================

The three valid programs (V1.harm, V2.harm, V3.harm) and five
malformed programs (I1.harm - I5.harm) from the D3 Test Report
are included under tests/.

Valid programs -- run them to hear/see the performance:
  java -cp out Main tests/V1.harm
  java -cp out Main tests/V2.harm
  java -cp out Main tests/V3.harm
Each prints its console trace; add --midi=NAME.mid to also save
a playable file. Their full traces are reproduced in the D3 report.

Malformed programs -- each is rejected with a line/column:
  I1.harm  -> Parse error at line 2, col 14: expected TIMES but found '{'
  I2.harm  -> Parse error at line 2, col 1: expected top-level declaration ... but found 'if'
  I3.harm  -> Parse error at line 1, col 8: expected IDENT but found 'int'
  I4.harm  -> Parse error at line 2, col 12: expected a duration ... but found ';'
  I5.harm  -> Parse error at line 3, col 16: assignment is a statement, not an expression (it cannot appear inside parentheses)

================================================================
CHANGES FROM PART 1
================================================================

Part 2 adds the type checker and interpreter required by the
spec, and delivers the four fixes the Part 1 D5 retrospective
committed to:

1. NOTE_LIT octave-digit guard.
   Identifiers like Arpeggio or Bar are no longer sliced into
   NOTE_LIT + IDENT pieces. The scanner uses a pure-lookahead
   helper looksLikeNote() that requires a valid octave tail
   before committing to a NOTE_LIT, else falls through to ident.

2. --dump-ast pretty-printer.
   Main.java + util/ASTPrinter.java implement the indented-tree
   dump format.

3. Block comments "(* ... *)".
   The lexer now recognises "(* ... *)" comments in addition to
   "// ..." line comments. Block comments may span multiple lines
   but do not nest (an inner "(*" is just text). An unterminated
   block comment raises a Lexer error at its starting line/column.

4. Improved error for "(y = 5)" (test case I5).
   Inside a parenthesised expression, an "=" where ")" was
   expected now reports "assignment is a statement, not an
   expression (it cannot appear inside parentheses)" instead of
   the generic "expected RPAREN".

NEW IN PART 2 (beyond the retrospective list):

5. Type checker (check/). Strong typing; the only coercion is
   int -> float widening (never the reverse). phrase is a single
   structured type whose equivalence is decided by its event
   constructor. Catches type errors before the program runs.

6. Interpreter (interp/). Tree-walking evaluator with static
   (lexical) scoping via a parent-pointer environment, pass-by-
   value parameters, short-circuit && / ||, and 'return' via a
   control-flow signal. One stream of musical events feeds three
   outputs through the NotePlayer interface (console / live MIDI
   / .mid file), so the three can never disagree.

================================================================
