int readint();
int writeint(int out);

int main0() {
    int a = readint();
    int b = readint();
    
    writeint(a > b);
    writeint(a < b);
    writeint(a >= b);
    writeint(a <= b);
    writeint(a == b);
    writeint(a != b);
    
    if (a > b) {
        writeint(1);
    } else {
        writeint(0);
    }
    
    return 0;
}
