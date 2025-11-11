package impl.symbol;

import framework.lang.Type;

public class Symbol {
    public enum Kind {
        VARIABLE,    // Variables and function names (in "other" namespace)
        FUNCTION,    // Actually same as variable, but we track separately
        FUNCTION_DECL, // Function declaration (no body)
        STRUCT_TAG,  // Structure tags
        STRUCT_MEMBER // Structure members (each struct has its own namespace)
    }

    private final String name;
    private final Type type;
    private final Kind kind;
    private final int scopeId;  // For tracking which scope this belongs to

    public Symbol(String name, Type type, Kind kind, int scopeId) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.scopeId = scopeId;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public Kind getKind() {
        return kind;
    }

    public int getScopeId() {
        return scopeId;
    }
}
