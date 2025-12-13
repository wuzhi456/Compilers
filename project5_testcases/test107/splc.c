int readint();
int writeint(int out);
int setseed(int seed);
int getrand();
int assert_eq(int where, int given, int expected);

// Test 107: Struct with array members
struct Student {
    int id;
    int scores[5];
    int total;
};

struct Student students[20];

int calculate_total(int idx) {
    int sum = 0;
    int i = 0;
    while (i < 5) {
        sum = sum + students[idx].scores[i];
        i = i + 1;
    }
    students[idx].total = sum;
    return sum;
}

int main0() {
    int n = readint();
    setseed(readint());
    
    int i = 0;
    while (i < n) {
        students[i].id = i + 1;
        int j = 0;
        while (j < 5) {
            students[i].scores[j] = getrand() % 101;
            j = j + 1;
        }
        i = i + 1;
    }
    
    i = 0;
    while (i < n) {
        int total = calculate_total(i);
        writeint(students[i].id);
        writeint(total);
        i = i + 1;
    }
    return 0;
}
