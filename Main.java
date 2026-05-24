import ast.Program;
import check.TypeChecker;
import check.TypeError;
import interp.ConsolePlayer;
import interp.Interpreter;
import interp.MidiFilePlayer;
import interp.MidiPlayer;
import interp.NotePlayer;
import interp.RuntimeError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lexer.Lexer;
import lexer.Token;
import parser.ParseError;
import parser.Parser;
import util.ASTPrinter;

/* Harmonia CLI entry point. */
public final class Main {

    private Main() { }

    public static void main(String[] args) {
        String sourceFile = null;
        boolean dumpAst = false;
        boolean typeCheck = false;
        boolean writeMidi = false;
        boolean noAudio = false;
        String midiPath = null;

        for (String a : args) {
            if (a.equals("--dump-ast")) {
                dumpAst = true;
            } else if (a.equals("--check")) {
                typeCheck = true;
            } else if (a.equals("--midi")) {
                writeMidi = true;
            } else if (a.startsWith("--midi=")) {
                writeMidi = true;
                midiPath = a.substring("--midi=".length());
            } else if (a.equals("--no-audio")) {
                noAudio = true;
            } else if (a.startsWith("--")) {
                System.err.println("error: unknown option: " + a);
                usage();
                System.exit(2);
            } else if (sourceFile == null) {
                sourceFile = a;
            } else {
                System.err.println("error: unexpected extra argument: " + a);
                usage();
                System.exit(2);
            }
        }

        if (sourceFile == null) {
            System.err.println("error: no source file given");
            usage();
            System.exit(2);
        }

        String source;
        try {
            source = Files.readString(Path.of(sourceFile));
        } catch (NoSuchFileException e) {
            System.err.println("error: source file not found: " + sourceFile);
            System.exit(1);
            return; // unreachable, but keeps the compiler happy about `source`
        } catch (IOException e) {
            System.err.println("error: could not read source file '" + sourceFile
                    + "': " + e.getMessage());
            System.exit(1);
            return;
        }

        List<Token> tokens;
        try {
            tokens = new Lexer(source).tokenize();
        } catch (RuntimeException e) {
            // The lexer signals scanning problems (unterminated block comment,
            // unexpected character) by throwing a RuntimeException whose message
            // already carries the line/column. Catch it here so the user sees a
            // clean "Lexer error: ..." line and a non-zero exit, instead of a
            // raw Java stack trace.
            System.err.println("Lexer error: " + e.getMessage());
            System.exit(1);
            return;
        }
        Program ast;
        try {
            ast = new Parser(tokens).parseProgram();
        } catch (ParseError e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        if (dumpAst) {
            ASTPrinter.print(ast);
            return;
        }

        // The interpreter assumes a type-correct AST, so type checking always
        // runs before execution (and is the whole job under --check).
        try {
            new TypeChecker(ast).check();
        } catch (TypeError e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        if (typeCheck) {
            System.out.println("OK: no type errors.");
            return;
        }

        // Build the output sinks. The console trace is always on; live audio is
        // on unless suppressed; the .mid file is added only with --midi.
        List<NotePlayer> players = new ArrayList<>();
        players.add(new ConsolePlayer(System.out));
        if (!noAudio) {
            players.add(new MidiPlayer());
        }
        if (writeMidi) {
            String path = (midiPath != null) ? midiPath : defaultMidiName(sourceFile);
            players.add(new MidiFilePlayer(path));
        }

        try {
            new Interpreter(players).run(ast);
        } catch (RuntimeError e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /** Derive a .mid filename from the source name: foo.harm -> foo.mid. */
    private static String defaultMidiName(String sourceFile) {
        String base = Path.of(sourceFile).getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base + ".mid";
    }

    private static void usage() {
        System.err.println("usage: java Main <source-file> "
                + "[--dump-ast] [--check] [--midi[=file.mid]] [--no-audio]");
    }
}