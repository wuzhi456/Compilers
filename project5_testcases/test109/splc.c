int readint();
int writeint(int out);
int setseed(int seed);
int getrand();
int assert_eq(int where, int given, int expected);

// Test 109: Complex pointer and struct operations
struct Data {
    int values[5];
    int sum;
};

struct Data data_array[10];

int compute_sum(struct Data *d) {
    int s = 0;
    int *ptr = &d->values[0];
    int i = 0;
    while (i < 5) {
        s = s + *(ptr + i);
        i = i + 1;
    }
    d->sum = s;
    return s;
}

int swap_data(struct Data *a, struct Data *b) {
    int temp = a->sum;
    a->sum = b->sum;
    b->sum = temp;
    return 0;
}

int main0() {
    int n = readint();
    setseed(readint());
    
    int i = 0;
    while (i < n) {
        int j = 0;
        while (j < 5) {
            data_array[i].values[j] = getrand() % 20;
            j = j + 1;
        }
        compute_sum(&data_array[i]);
        i = i + 1;
    }
    
    // Sort by sum (simple bubble sort)
    i = 0;
    while (i < n - 1) {
        int j = 0;
        while (j < n - i - 1) {
            if (data_array[j].sum > data_array[j + 1].sum) {
                swap_data(&data_array[j], &data_array[j + 1]);
            }
            j = j + 1;
        }
        i = i + 1;
    }
    
    i = 0;
    while (i < n) {
        writeint(data_array[i].sum);
        i = i + 1;
    }
    return 0;
}
