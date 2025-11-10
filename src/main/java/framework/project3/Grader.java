package framework.project3;

import java.io.*;

import framework.AbstractCompiler;
import framework.AbstractGrader;
import impl.Compiler;

public class Grader extends AbstractGrader {
    public Grader(InputStream sourceStream, OutputStream outputStream) {
        super(sourceStream, outputStream);
    }

    public Grader(InputStream sourceStream) {
        super(sourceStream);
    }

    @Override
    public void run() throws IOException {
        AbstractCompiler compiler = new Compiler(this);
        compiler.start();
    }
}