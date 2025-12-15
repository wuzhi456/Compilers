int readint();
int writeint(int out);

struct Data {
    int a;
    int b;
};

struct Data arr[5];

int main0() {
    int n = readint();
    int i = 0;
    
    while (i < n) {
        arr[i].a = readint();
        arr[i].b = readint();
        i = i + 1;
    }
    
    i = 0;
    while (i < n) {
        writeint(arr[i].a + arr[i].b);
        i = i + 1;
    }
    
    return 0;
}
