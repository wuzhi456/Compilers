int readint();
int writeint(int out);

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
