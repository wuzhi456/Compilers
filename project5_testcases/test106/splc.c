int readint();
int writeint(int out);

// Test 106: Simple struct operations
struct Point {
    int x;
    int y;
};

int main0() {
    struct Point p;
    p.x = readint();
    p.y = readint();
    
    writeint(p.x);
    writeint(p.y);
    writeint(p.x + p.y);
    writeint(p.x * p.y);
    
    return 0;
}
