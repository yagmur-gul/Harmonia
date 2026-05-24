package interp;

import java.io.PrintStream;

/* A {@link NotePlayer} that writes a human-readable trace of the performance —
 * the artifact for the test report. It produces no sound and touches no
 * external resources; it just narrates each event in order. */
public final class ConsolePlayer implements NotePlayer {

    private final PrintStream out;
    private int currentVelocity = 64; // mirrors the interpreter's default

    public ConsolePlayer(PrintStream out) {
        this.out = out;
    }

    @Override public void begin() {
        out.println("--- performance trace ---");
    }

    @Override public void setTempo(int bpm) {
        out.println("tempo  -> " + bpm + " BPM");
    }

    @Override public void setVolume(int velocity) {
        this.currentVelocity = velocity;
        out.println("volume -> velocity " + velocity);
    }

    @Override public void playNote(int midi, int durationMs) {
        // Show both the readable name and the raw MIDI number so the trace is
        // self-checking against the C4=60 formula, plus the velocity in force.
        out.printf("note   %-4s (MIDI %3d)  %5d ms  vel %d%n",
                   Pitch.midiToName(midi), midi, durationMs, currentVelocity);
    }

    @Override public void end() {
        out.println("--- end of performance ---");
    }
}