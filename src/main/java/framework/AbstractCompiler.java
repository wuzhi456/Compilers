package framework;

import java.io.IOException;

public abstract class AbstractCompiler {
    public final AbstractGrader grader;

    // Do not modify this constructor!!
    public AbstractCompiler(AbstractGrader grader) {
        this.grader = grader;
    }

    public abstract void start() throws IOException;
}
