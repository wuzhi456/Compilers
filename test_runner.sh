#!/bin/bash
# Simple test runner for project3 test cases

for testfile in testcases/project3/*.splc; do
    basename=$(basename "$testfile" .splc)
    expected="testcases/project3/${basename}.txt"
    
    echo "Testing: $basename"
    
    # Create a temporary Main.java
    cat > /tmp/TempMain.java << EOF
import framework.AbstractGrader;
import framework.project3.Grader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class TempMain {
    public static void main(String[] args) throws IOException {
        InputStream input = new FileInputStream("$testfile");
        AbstractGrader grader = new Grader(input, System.out);
        grader.run();
    }
}
EOF
    
    # Compile and run
    javac -cp "libs/antlr-4.13.2-complete.jar:out/production" /tmp/TempMain.java -d /tmp 2>/dev/null
    actual=$(java -cp "libs/antlr-4.13.2-complete.jar:out/production:/tmp" TempMain 2>&1)
    expected_output=$(cat "$expected")
    
    if [ "$actual" = "$expected_output" ]; then
        echo "  ✓ PASS"
    else
        echo "  ✗ FAIL"
        echo "  Expected: $expected_output"
        echo "  Actual:   $actual"
    fi
    echo
done
