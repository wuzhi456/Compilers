int readint();
int writeint(int out);
int setseed(int seed);
int getrand();
int assert_eq(int where, int given, int expected);

// Test 103: 2D array operations
int matrix[10][10];

int transpose(int n) {
    int i = 0;
    while (i < n) {
        int j = i + 1;
        while (j < n) {
            int temp = matrix[i][j];
            matrix[i][j] = matrix[j][i];
            matrix[j][i] = temp;
            j = j + 1;
        }
        i = i + 1;
    }
    return 0;
}

int main0() {
    int n = readint();
    setseed(readint());
    
    // Initialize matrix
    int i = 0;
    while (i < n) {
        int j = 0;
        while (j < n) {
            matrix[i][j] = getrand() % 50;
            j = j + 1;
        }
        i = i + 1;
    }
    
    // Transpose
    transpose(n);
    
    // Print matrix
    i = 0;
    while (i < n) {
        int j = 0;
        while (j < n) {
            writeint(matrix[i][j]);
            j = j + 1;
        }
        i = i + 1;
    }
    return 0;
}
