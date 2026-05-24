package interp;

import java.util.HashMap;
import java.util.Map;

/**
 * The run-time environment: a chain of lexical scopes that mirrors the
 * compile-time, but stores actual *values* instead of types.
 */
public final class Environment {

    /** The lexically enclosing scope, or null for the global scope. */
    private final Environment parent;

    /** Names bound in *this* scope only. */
    private final Map<String, Object> values = new HashMap<>();

    /** Create the global scope (no enclosing scope). */
    public Environment() {
        this.parent = null;
    }

    /** Create an inner scope enclosed by {@code parent}. */
    public Environment(Environment parent) {
        this.parent = parent;
    }

    /* Bind a new name in the current (innermost) scope. The type checker has
     * already rejected duplicate declarations in the same scope, so we do not
     * re-check that here; a plain put is correct. */
    public void define(String name, Object value) {
        values.put(name, value);
    }

    /* Read a variable, walking outward through enclosing scopes — the run-time
     * counterpart of TypeEnv.lookupVar. The type checker guarantees the name is
     * bound somewhere on this chain, so the final throw is a "should never
     * happen" guard, not a user-facing error path. */
    public Object get(String name) {
        for (Environment e = this; e != null; e = e.parent) {
            if (e.values.containsKey(name)) {
                return e.values.get(name);
            }
        }
        throw new IllegalStateException(
            "unbound variable '" + name + "' reached the interpreter; "
                + "the type checker should have caught this");
    }

    /* Assign to an existing variable, updating it in whichever enclosing scope
     * actually declared it (not creating a new binding here). */
    public void assign(String name, Object value) {
        for (Environment e = this; e != null; e = e.parent) {
            if (e.values.containsKey(name)) {
                e.values.put(name, value);
                return;
            }
        }
        throw new IllegalStateException(
            "assignment to unbound variable '" + name + "' reached the "
                + "interpreter; the type checker should have caught this");
    }
}