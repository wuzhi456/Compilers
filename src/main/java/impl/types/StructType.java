package impl.types;

import framework.lang.Type;
import java.util.ArrayList;
import java.util.List;

public class StructType implements Type {
    private final String tag;
    private final List<Member> members;
    private final boolean isComplete;

    public static class Member {
        public final Type type;
        public final String name;

        public Member(Type type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    // Constructor for incomplete struct
    public StructType(String tag) {
        this.tag = tag;
        this.members = null;
        this.isComplete = false;
    }

    // Constructor for complete struct
    public StructType(String tag, List<Member> members) {
        this.tag = tag;
        this.members = new ArrayList<>(members);
        this.isComplete = true;
    }

    public String getTag() {
        return tag;
    }

    public List<Member> getMembers() {
        return members;
    }

    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public String prettyPrint() {
        return "struct " + tag;
    }

    @Override
    public String fullPrint() {
        if (!isComplete || members == null) {
            return prettyPrint();
        }
        StringBuilder sb = new StringBuilder("struct ");
        sb.append(tag).append("{");
        for (Member member : members) {
            sb.append(member.type.prettyPrint()).append(" ").append(member.name).append(";");
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StructType)) return false;
        StructType other = (StructType) obj;
        // Two struct types are the same if they have the same tag
        // and same completeness (in same scope context, but we check that elsewhere)
        return tag.equals(other.tag);
    }

    @Override
    public int hashCode() {
        return tag.hashCode();
    }
}
