int readint();
int writeint(int out);

int main0() {
    int n = readint();
    int sum = 0;
    int i = 0;
    
    while (i < n) {
        sum = sum + i;
        i = i + 1;
    }
    
    writeint(sum);
    writeint(i);
    
    return 0;
}
