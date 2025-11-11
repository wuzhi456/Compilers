package impl.types;

import framework.lang.Type;

public class ArrayType implements Type {
    private final Type elementType;
    private final int length;

    public ArrayType(Type elementType, int length) {
        this.elementType = elementType;
        this.length = length;
    }

    public Type getElementType() {
        return elementType;
    }

    public int getLength() {
        return length;
    }

    @Override
    public String prettyPrint() {
        return elementType.prettyPrint() + "[" + length + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ArrayType)) return false;
        ArrayType other = (ArrayType) obj;
        return length == other.length && elementType.equals(other.elementType);
    }

    @Override
    public int hashCode() {
        return elementType.hashCode() * 31 + length;
    }
}
