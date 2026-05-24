package interp;

/* Carries a {@code return}'s value out of a func body. */
final class ReturnSignal extends RuntimeException {
    final Object value;

    ReturnSignal(Object value) {
        super(null, null, false, false);
        this.value = value;
    }
}