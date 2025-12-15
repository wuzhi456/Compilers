int readint();
int writeint(int out);

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
