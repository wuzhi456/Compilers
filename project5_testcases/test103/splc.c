int readint();
int writeint(int out);

// Test 103: Array read and write
int arr[10];

int main0() {
    int n = readint();
    int i = 0;
    
    while (i < n) {
        arr[i] = readint();
        i = i + 1;
    }
    
    i = 0;
    while (i < n) {
        writeint(arr[i]);
        i = i + 1;
    }
    
    writeint(arr[0] + arr[n - 1]);
    
    return 0;
}
