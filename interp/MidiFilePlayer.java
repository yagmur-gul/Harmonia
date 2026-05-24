package interp;

import java.io.File;
import java.io.IOException;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

/* A {@link NotePlayer} that records the performance into a Standard MIDI File
 * (a {@code .mid}), written when the {@code --midi} flag is given. */
public final class MidiFilePlayer implements NotePlayer {

    /** Ticks per quarter note — the file's time resolution. */
    private static final int PPQ = 480;

    private final String path;

    private Sequence sequence;
    private Track track;
    private long currentTick = 0;
    private int bpm = 120;       // mirrors the interpreter's default tempo
    private int velocity = 64;   // mirrors the interpreter's default volume

    public MidiFilePlayer(String path) {
        this.path = path;
    }

    @Override public void begin() {
        try {
            sequence = new Sequence(Sequence.PPQ, PPQ);
            track = sequence.createTrack();
            writeTempoMeta(bpm); // record the starting tempo at tick 0
        } catch (InvalidMidiDataException e) {
            // PPQ division is always valid, so this is unreachable in practice.
            throw new IllegalStateException("could not create MIDI sequence", e);
        }
    }

    @Override public void setTempo(int bpm) {
        this.bpm = bpm;
        writeTempoMeta(bpm); // a tempo meta-event at the current cursor
    }

    @Override public void setVolume(int velocity) {
        this.velocity = velocity;
    }

    @Override public void playNote(int midi, int durationMs) {
        long lengthTicks = msToTicks(durationMs);
        addNoteEvent(ShortMessage.NOTE_ON,  midi, velocity, currentTick);
        addNoteEvent(ShortMessage.NOTE_OFF, midi, 0,        currentTick + lengthTicks);
        currentTick += lengthTicks; // notes are sequential, so advance the cursor
    }

    @Override public void end() {
        try {
            // Type 0 = a single-track file; we only use one track.
            MidiSystem.write(sequence, 0, new File(path));
            System.out.println("wrote MIDI file: " + path);
        } catch (IOException e) {
            System.err.println("error: could not write MIDI file '" + path
                    + "': " + e.getMessage());
        }
    }

    // ---- helpers ----

    /** Convert a millisecond duration at the current BPM into timeline ticks. */
    private long msToTicks(int ms) {
        double msPerQuarter = 60000.0 / bpm;
        return Math.round(ms * PPQ / msPerQuarter);
    }

    private void addNoteEvent(int command, int midi, int vel, long tick) {
        try {
            ShortMessage msg = new ShortMessage();
            msg.setMessage(command, 0 /* channel */, midi, vel);
            track.add(new MidiEvent(msg, tick));
        } catch (InvalidMidiDataException e) {
            // midi and vel are already range-checked upstream, so unreachable.
            throw new IllegalStateException("invalid MIDI event", e);
        }
    }

    /**
     * Emit a set-tempo meta-event (type 0x51) at the current cursor. Its three
     * data bytes are microseconds-per-quarter-note, big-endian — the MIDI
     * standard's way of storing tempo.
     */
    private void writeTempoMeta(int bpm) {
        int microsPerQuarter = (int) Math.round(60_000_000.0 / bpm);
        byte[] data = {
            (byte) ((microsPerQuarter >> 16) & 0xFF),
            (byte) ((microsPerQuarter >> 8)  & 0xFF),
            (byte) (microsPerQuarter         & 0xFF)
        };
        try {
            javax.sound.midi.MetaMessage meta = new javax.sound.midi.MetaMessage();
            meta.setMessage(0x51, data, data.length);
            track.add(new MidiEvent(meta, currentTick));
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException("invalid tempo meta-event", e);
        }
    }
}
