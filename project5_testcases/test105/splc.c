int readint();
int writeint(int out);
int setseed(int seed);
int getrand();
int assert_eq(int where, int given, int expected);

// Test 105: Short-circuit evaluation and increment/decrement
int counter;

int increment_and_check(int limit) {
    counter = counter + 1;
    return counter <= limit;
}

int decrement_and_check(int limit) {
    counter = counter - 1;
    return counter >= limit;
}

int main0() {
    int tests = readint();
    int t = 0;
    while (t < tests) {
        counter = readint();
        int op = readint();
        int limit = readint();
        
        if (op == 1) {
            // Test &&
            if (counter > 0 && increment_and_check(limit)) {
                writeint(1);
            } else {
                writeint(0);
            }
        } else {
            // Test ||
            if (counter < 0 || decrement_and_check(limit)) {
                writeint(1);
            } else {
                writeint(0);
            }
        }
        writeint(counter);
        t = t + 1;
    }
    return 0;
}
