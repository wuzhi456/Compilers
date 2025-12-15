int readint();
int writeint(int out);

// Test 100: Basic arithmetic operations
int main0() {
    int a = readint();
    int b = readint();
    
    writeint(a + b);
    writeint(a - b);
    writeint(a * b);
    writeint(a / b);
    writeint(a % b);
    writeint(-a);
    
    return 0;
}
