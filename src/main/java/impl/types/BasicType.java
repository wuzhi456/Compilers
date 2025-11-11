package impl.types;

import framework.lang.Type;

public class BasicType implements Type {
    public enum Kind {
        INT, CHAR
    }

    private final Kind kind;

    public BasicType(Kind kind) {
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public String prettyPrint() {
        return kind == Kind.INT ? "int" : "char";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BasicType)) return false;
        BasicType other = (BasicType) obj;
        return kind == other.kind;
    }

    @Override
    public int hashCode() {
        return kind.hashCode();
    }
}
