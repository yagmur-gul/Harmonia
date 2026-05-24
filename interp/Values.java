package interp;

import ast.Duration;
import java.util.List;

/* Run-time representations of Harmonia values. */
public final class Values {

    private Values() { }

    /* A pitch, stored as a MIDI note number (C4 = 60). */
    public record Note(int midi) {
        @Override public String toString() {
            return Pitch.midiToName(midi) + " (MIDI " + midi + ")";
        }
    }

    /* One playable event: a pitch together with how long it sounds. */
    public record Event(Note pitch, Duration duration) {
        @Override public String toString() {
            return pitch + " " + duration.name().toLowerCase();
        }
    }

    /* A phrase: an ordered, immutable list of events. */
    public record Phrase(List<Event> events) {
        public Phrase(List<Event> events) {
            this.events = List.copyOf(events);
        }
    }
}