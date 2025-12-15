int readint();
int writeint(int out);

struct Item {
    int value;
};

struct Item items[5];

int set_value(struct Item *p, int v) {
    p->value = v;
    return 0;
}

int main0() {
    int n = readint();
    int i = 0;
    
    while (i < n) {
        int v = readint();
        set_value(&items[i], v);
        i = i + 1;
    }
    
    i = 0;
    while (i < n) {
        writeint(items[i].value);
        i = i + 1;
    }
    
    return 0;
}
