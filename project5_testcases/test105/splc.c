int readint();
int writeint(int out);

// Test 105: Short-circuit evaluation
int main0() {
    int a = readint();
    int b = readint();
    
    if (a > 0 && b > 0) {
        writeint(1);
    } else {
        writeint(0);
    }
    
    if (a < 0 || b < 0) {
        writeint(1);
    } else {
        writeint(0);
    }
    
    if (a == 0 && b == 0) {
        writeint(1);
    } else {
        writeint(0);
    }
    
    return 0;
}
