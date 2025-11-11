package impl.types;

import framework.lang.Type;

public class PointerType implements Type {
    private final Type referencedType;

    public PointerType(Type referencedType) {
        this.referencedType = referencedType;
    }

    public Type getReferencedType() {
        return referencedType;
    }

    @Override
    public String prettyPrint() {
        return referencedType.prettyPrint() + "*";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PointerType)) return false;
        PointerType other = (PointerType) obj;
        return referencedType.equals(other.referencedType);
    }

    @Override
    public int hashCode() {
        return referencedType.hashCode() * 17;
    }
}
