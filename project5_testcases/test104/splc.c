int readint();
int writeint(int out);

// Test 104: Simple function calls
int add(int a, int b) {
    return a + b;
}

int multiply(int a, int b) {
    return a * b;
}

int main0() {
    int x = readint();
    int y = readint();
    
    int sum = add(x, y);
    int prod = multiply(x, y);
    
    writeint(sum);
    writeint(prod);
    writeint(add(sum, prod));
    
    return 0;
}
