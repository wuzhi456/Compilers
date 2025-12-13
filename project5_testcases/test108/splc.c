int readint();
int writeint(int out);
int setseed(int seed);
int getrand();
int assert_eq(int where, int given, int expected);

// Test 108: Struct with pointers
struct Node {
    int value;
    int count;
};

struct Node nodes[100];
int node_count;

int init_node(struct Node *n, int val) {
    n->value = val;
    n->count = 0;
    return 0;
}

int increment_count(struct Node *n) {
    n->count = n->count + 1;
    return n->count;
}

int main0() {
    int n = readint();
    node_count = n;
    
    int i = 0;
    while (i < n) {
        int val = readint();
        init_node(&nodes[i], val);
        i = i + 1;
    }
    
    // Increment counts based on values
    i = 0;
    while (i < n) {
        struct Node *p = &nodes[i];
        int j = 0;
        while (j < p->value % 5 + 1) {
            increment_count(p);
            j = j + 1;
        }
        i = i + 1;
    }
    
    // Output results
    i = 0;
    while (i < n) {
        writeint(nodes[i].value);
        writeint(nodes[i].count);
        i = i + 1;
    }
    return 0;
}
