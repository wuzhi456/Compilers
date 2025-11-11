package impl.types;

import framework.lang.Type;
import java.util.ArrayList;
import java.util.List;

public class FunctionType implements Type {
    private final Type returnType;
    private final List<Type> parameterTypes;

    public FunctionType(Type returnType, List<Type> parameterTypes) {
        this.returnType = returnType;
        this.parameterTypes = new ArrayList<>(parameterTypes);
    }

    public Type getReturnType() {
        return returnType;
    }

    public List<Type> getParameterTypes() {
        return parameterTypes;
    }

    @Override
    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append(returnType.prettyPrint()).append("(");
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(parameterTypes.get(i).prettyPrint());
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FunctionType)) return false;
        FunctionType other = (FunctionType) obj;
        return returnType.equals(other.returnType) && 
               parameterTypes.equals(other.parameterTypes);
    }

    @Override
    public int hashCode() {
        return returnType.hashCode() * 31 + parameterTypes.hashCode();
    }
}
