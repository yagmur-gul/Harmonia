package check;

import ast.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The compile-time environment used by the type checker.
public final class TypeEnv {

    // ---- callable signatures ----

    // A melody is void (no return type); a func returns a value.
    public record FuncSig(Type returnType, List<Type> paramTypes) { }
    public record MelodySig(List<Type> paramTypes) { }

    private final Map<String, FuncSig>   funcs    = new HashMap<>();
    private final Map<String, MelodySig> melodies = new HashMap<>();

    // ---- lexical variable scopes ----

    // Each scope maps a variable name to its declared type. The list is used
    // as a stack: index 0 is the global scope, the last element is innermost.
    private final List<Map<String, Type>> scopes = new ArrayList<>();

    public TypeEnv() {
        scopes.add(new HashMap<>()); // global scope
    }

    // ---- scope management ----

    public void pushScope() {
        scopes.add(new HashMap<>());
    }

    public void popScope() {
        scopes.remove(scopes.size() - 1);
    }

    // Declare a variable in the *current* (innermost) scope.
    // Returns false if a variable with this name already exists in this same
    // scope (a redeclaration); the caller turns that into a TypeError. Shadowing
    // a name from an *outer* scope is allowed and returns true.
    public boolean declareVar(String name, Type type) {
        Map<String, Type> current = scopes.get(scopes.size() - 1);
        if (current.containsKey(name)) {
            return false;
        }
        current.put(name, type);
        return true;
    }

    // Look a variable up, walking outward from the innermost scope. Returns null
    // if the name is not bound in any enclosing scope.
    public Type lookupVar(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Type t = scopes.get(i).get(name);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    // ---- callable registration and lookup ----

    public boolean declareFunc(String name, FuncSig sig) {
        if (funcs.containsKey(name) || melodies.containsKey(name)) {
            return false;
        }
        funcs.put(name, sig);
        return true;
    }

    public boolean declareMelody(String name, MelodySig sig) {
        if (funcs.containsKey(name) || melodies.containsKey(name)) {
            return false;
        }
        melodies.put(name, sig);
        return true;
    }

    public FuncSig lookupFunc(String name)       { return funcs.get(name); }
    public MelodySig lookupMelody(String name)   { return melodies.get(name); }
    public boolean isCallable(String name) {
        return funcs.containsKey(name) || melodies.containsKey(name);
    }
}