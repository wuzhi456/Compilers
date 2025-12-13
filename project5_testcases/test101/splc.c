int readint();
int writeint(int out);
int setseed(int seed);
int getrand();
int assert_eq(int where, int given, int expected);

// Test 101: Arrays and loops
int arr[100];

int bubble_sort(int n) {
    int i = 0;
    while (i < n - 1) {
        int j = 0;
        while (j < n - i - 1) {
            if (arr[j] > arr[j + 1]) {
                int temp = arr[j];
                arr[j] = arr[j + 1];
                arr[j + 1] = temp;
            }
            j = j + 1;
        }
        i = i + 1;
    }
    return 0;
}

int main0() {
    int n = readint();
    setseed(readint());
    
    int i = 0;
    while (i < n) {
        arr[i] = getrand() % 100;
        i = i + 1;
    }
    
    bubble_sort(n);
    
    i = 0;
    while (i < n) {
        writeint(arr[i]);
        i = i + 1;
    }
    return 0;
}
