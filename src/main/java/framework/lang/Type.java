package framework.lang;

public interface Type {
    String prettyPrint();

    default String fullPrint() {
        return prettyPrint();
    }
}
