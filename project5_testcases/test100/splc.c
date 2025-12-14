int readint();
int writeint(int out);
int setseed(int seed);
int getrand();
int assert_eq(int where, int given, int expected);

// Test 100: Basic arithmetic and function calls
int factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

int main0() {
    int n = readint();
    int result = factorial(n);
    writeint(result);
    
    // Test some basic arithmetic
    int a = readint();
    int b = readint();
    writeint(a + b);
    writeint(a - b);
    writeint(a * b);
    if (b != 0) {
        writeint(a / b);
        writeint(a % b);
    }
    return 0;
}
