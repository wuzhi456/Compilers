int readint();
int writeint(int out);

struct Pair {
    int first;
    int second;
};

struct Pair pairs[5];

int add_pair(struct Pair *p) {
    return p->first + p->second;
}

int main0() {
    int n = readint();
    int i = 0;
    
    while (i < n) {
        pairs[i].first = readint();
        pairs[i].second = readint();
        i = i + 1;
    }
    
    i = 0;
    while (i < n) {
        struct Pair *p = &pairs[i];
        writeint(add_pair(p));
        i = i + 1;
    }
    
    return 0;
}
