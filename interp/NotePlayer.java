package interp;

/* The sink the interpreter sends musical events to. The tree-walker never decides *how* a note is realized
 * it just announces what happened, and each attached player reacts. */
public interface NotePlayer {

    /** Called once before any events, so a player can acquire resources. */
    default void begin() { }

    /** A {@code tempo N} statement ran; N is the new beats-per-minute. */
    void setTempo(int bpm);

    /** A {@code volume N} statement ran; v is the new MIDI velocity (0–127). */
    void setVolume(int velocity);

    /**
     * Sound one note.
     *
     * @param midi      the pitch as a MIDI number (already transposed, already
     *                  verified to be in 0–127 by the interpreter)
     * @param durationMs how long the note sounds, in milliseconds
     */
    void playNote(int midi, int durationMs);

    /** Called once after all events, so a player can flush and release. */
    default void end() { }
}