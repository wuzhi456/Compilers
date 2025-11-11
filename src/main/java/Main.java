import framework.AbstractGrader;
import framework.project3.Grader;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class Main {
    public static void main(String[] args) throws IOException {
        InputStream input = System.in;
        if (args.length > 0) {
            input = new FileInputStream(args[0]);
        }
        AbstractGrader grader = new Grader(input, System.out);
        grader.run();
    }
}