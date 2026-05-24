package interp;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;

/* A {@link NotePlayer} that produces real sound through the JVM's built-in
 * software synthesizer ({@code javax.sound.midi}). No external library is
 * needed — the Java SE platform ships a General-MIDI synth. */
public final class MidiPlayer implements NotePlayer {

    private Synthesizer synth;
    private MidiChannel channel;
    private int velocity = 64;     // default until a volume statement runs
    private boolean available = false;

    @Override public void begin() {
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            // Channel 0 is a fine default; on a freshly opened synth it is the
            // Acoustic Grand Piano (General MIDI program 0).
            channel = synth.getChannels()[0];
            available = true;
        } catch (MidiUnavailableException e) {
            System.err.println(
                "warning: no MIDI synthesizer available, playing silently "
                    + "(timing preserved): " + e.getMessage());
            available = false;
        }
    }

    @Override public void setTempo(int bpm) {
        // Tempo affects only how long notes are held, and the interpreter has
        // already folded that into the millisecond duration it passes to
        // playNote. A live synth has no global tempo to set, so nothing to do.
    }

    @Override public void setVolume(int velocity) {
        this.velocity = velocity;
    }

    @Override public void playNote(int midi, int durationMs) {
        if (available) {
            channel.noteOn(midi, velocity);
            sleep(durationMs);
            channel.noteOff(midi);
        } else {
            sleep(durationMs); // keep wall-clock timing identical
        }
    }

    @Override public void end() {
        if (available && synth != null) {
            synth.close();
        }
    }

    /** Sleep, treating an interrupt as "stop waiting" rather than an error. */
    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}