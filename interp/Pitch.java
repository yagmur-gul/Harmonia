package interp;

import ast.Accidental;
import ast.NoteLit;
import ast.NoteName;

/* The note-name &harr; MIDI-number conversion, in one place. */
public final class Pitch {

    private Pitch() { }

    /** Semitone offset of each natural letter above C, within one octave. */
    private static int semitoneOf(NoteName letter) {
        return switch (letter) {
            case C -> 0;
            case D -> 2;
            case E -> 4;
            case F -> 5;
            case G -> 7;
            case A -> 9;
            case B -> 11;
        };
    }

    /** A sharp adds a semitone, a flat removes one, natural changes nothing. */
    private static int accidentalShift(Accidental acc) {
        return switch (acc) {
            case SHARP   -> 1;
            case FLAT    -> -1;
            case NATURAL -> 0;
        };
    }

    /* Resolve a note literal from the AST to its MIDI number. */
    public static int toMidi(NoteLit n) {
        return 12 * (n.octave() + 1)
                + semitoneOf(n.letter())
                + accidentalShift(n.accidental());
    }

    /* Render a MIDI number back to a readable name for the console trace, e.g. 61 -> "C#4". */
    public static String midiToName(int midi) {
        String[] names =
            { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
        int octave = midi / 12 - 1;        // inverse of 12*(octave+1)
        int pc = ((midi % 12) + 12) % 12;  // pitch class, safe for negatives
        return names[pc] + octave;
    }
}