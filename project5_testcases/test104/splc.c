int readint();
int writeint(int out);
int setseed(int seed);
int getrand();
int assert_eq(int where, int given, int expected);

// Test 104: Nested conditionals and comparisons
int global_count;

int classify(int x) {
    if (x < 0) {
        global_count = global_count + 1;
        return -1;
    } else {
        if (x == 0) {
            return 0;
        } else {
            if (x <= 10) {
                return 1;
            } else {
                if (x <= 100) {
                    return 2;
                } else {
                    return 3;
                }
            }
        }
    }
}

int main0() {
    global_count = 0;
    int n = readint();
    int i = 0;
    while (i < n) {
        int x = readint();
        int cat = classify(x);
        writeint(cat);
        i = i + 1;
    }
    writeint(global_count);
    return 0;
}
