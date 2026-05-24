# Harmonia — A DSL for Music Composition

Harmonia is a domain-specific language for composing and playing musical
pieces. It ships with a lexer, a recursive-descent parser, a static type
checker, and a tree-walking interpreter. A program can be executed to produce
music through three outputs: a console performance trace, live audio via the
JVM's built-in MIDI synthesizer, and an optional Standard MIDI File (`.mid`).

> Built for CSE 341 (Programming Language Concepts). Part 1 delivered the lexer,
> parser, and an AST pretty-printer (`--dump-ast`); Part 2 added the static type
> checker (`--check`) and the interpreter.

## Requirements

- **Java 21 or later** — uses pattern matching in `switch` over sealed
  interfaces, a Java 21 language feature.
- No external libraries or build tools required.
- Live audio uses `javax.sound.midi` (part of the standard JDK).

## Project Structure

```
.
├── Main.java              CLI entry point
├── lexer/
│   ├── Lexer.java         Token scanner (// and (* *) comments)
│   ├── Token.java         Token data class
│   └── TokenType.java     Token type enum
├── parser/
│   ├── Parser.java        Recursive-descent parser
│   └── ParseError.java    Parse exception
├── ast/                   27 AST node classes as sealed records,
│                          plus 4 sealed interfaces and 4 enums
│   ├── Program.java       Root AST node
│   ├── Decl.java          Declaration interface
│   ├── Stmt.java          Statement interface
│   ├── Expr.java          Expression interface
│   └── ...
├── check/
│   ├── TypeChecker.java   Static type checker (two-pass)
│   ├── TypeEnv.java       Compile-time scopes + callable table
│   └── TypeError.java     Type-error exception (with line/col)
├── interp/
│   ├── Interpreter.java   Tree-walking evaluator
│   ├── Environment.java   Runtime scopes (parent-pointer chain)
│   ├── Values.java        Runtime value classes (note/event/phrase)
│   ├── Pitch.java         NoteLit <-> MIDI number (C4 = 60)
│   ├── NotePlayer.java    Output interface (the event sink)
│   ├── ConsolePlayer.java     Prints the performance trace
│   ├── MidiPlayer.java        Live audio via javax.sound.midi
│   ├── MidiFilePlayer.java    Writes a .mid file
│   ├── RuntimeError.java      Runtime-error exception (line/col)
│   └── ReturnSignal.java      Control-flow signal for 'return'
└── util/
    └── ASTPrinter.java    Indented tree pretty-printer
```

## Compile

From inside the project directory:

**macOS / Linux**

```bash
javac -d out lexer/*.java ast/*.java parser/*.java check/*.java util/*.java interp/*.java Main.java
```

**Windows (Command Prompt)**

```bat
javac -d out lexer\*.java ast\*.java parser\*.java check\*.java util\*.java interp\*.java Main.java
```

This compiles all sources into an `out/` directory.

## Usage

```bash
java -cp out Main <source-file> [flags]
```

| Flag            | Description |
| --------------- | ----------- |
| `--dump-ast`    | Parse the file and print the AST as an indented tree, then exit. (No type check / run.) |
| `--check`       | Parse and type-check only. Prints `OK: no type errors.` on success, or a type error and exits non-zero on failure. Does not run the program. |
| `--midi[=file]` | Also write a Standard MIDI File. With `=file` it writes to that path; bare `--midi` writes a default file name. Without this flag, no `.mid` is written. |
| `--no-audio`    | Skip live audio playback (still prints the trace and still writes the `.mid` file if `--midi` is given). |

With no mode flag, `Main` parses, type-checks, then runs the program: it prints
the console trace, plays live audio, and (if `--midi` was passed) writes the
`.mid` file.

**Pipeline.** Every run is parse → type-check → interpret. The interpreter
relies on the type checker having passed, so a program that fails `--check`
will not be run.

### Examples

```bash
java -cp out Main tests/V1.harm --dump-ast
java -cp out Main tests/V1.harm --check
java -cp out Main tests/V1.harm
java -cp out Main tests/V1.harm --midi=V1.mid --no-audio
```

## Errors

Every error is reported with a line and column, and the process exits with a
non-zero status.

| Kind          | Format |
| ------------- | ------ |
| Lexer error   | `Lexer error: <description> at line L, column C` |
| Parse error   | `Parse error at line L, col C: <description>` |
| Type error    | `Type error at line L, col C: <description>` |
| Runtime error | `Runtime error at line L, col C: <description>` |

Lexer errors are raised for an unexpected character and for an unterminated
block comment (reported at its starting position). Runtime errors are raised
for division by zero, phrase index out of range, and the domain-specific case
of a transpose that pushes a note outside the valid MIDI range 0–127.

## Test Programs

The `tests/` directory contains three valid programs (`V1.harm`, `V2.harm`,
`V3.harm`) and five malformed programs (`I1.harm`–`I5.harm`).

Run a valid program to hear and see the performance:

```bash
java -cp out Main tests/V1.harm
```

Add `--midi=NAME.mid` to also save a playable file.

Each malformed program is rejected with a precise line/column:

| File      | Error |
| --------- | ----- |
| `I1.harm` | Parse error at line 2, col 14: expected TIMES but found `{` |
| `I2.harm` | Parse error at line 2, col 1: expected top-level declaration but found `if` |
| `I3.harm` | Parse error at line 1, col 8: expected IDENT but found `int` |
| `I4.harm` | Parse error at line 2, col 12: expected a duration but found `;` |
| `I5.harm` | Parse error at line 3, col 16: assignment is a statement, not an expression (it cannot appear inside parentheses) |

## Design Notes

- **Strong typing.** The only implicit coercion is `int → float` widening
  (never the reverse). `phrase` is a single structured type whose equivalence
  is decided by its event constructor. Type errors are caught before the
  program runs.
- **Lexical scoping.** The interpreter uses a parent-pointer environment for
  static scoping, pass-by-value parameters, short-circuit `&&` / `||`, and
  `return` implemented via a control-flow signal.
- **One event stream, three outputs.** A single stream of musical events feeds
  the console trace, live MIDI playback, and the `.mid` file writer through the
  `NotePlayer` interface, so the three outputs can never disagree.
- **Scanner robustness.** A pure-lookahead helper (`looksLikeNote()`) requires
  a valid octave tail before committing to a `NOTE_LIT`, so identifiers like
  `Arpeggio` or `Bar` are not sliced into `NOTE_LIT + IDENT` pieces.
