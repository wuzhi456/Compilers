int readint();
int writeint(int out);
int setseed(int seed);
int getrand();
int assert_eq(int where, int given, int expected);

// Test 106: Struct with basic members
struct Point {
    int x;
    int y;
};

struct Rectangle {
    struct Point topLeft;
    struct Point bottomRight;
};

int calculate_area(struct Rectangle rect) {
    int width = rect.bottomRight.x - rect.topLeft.x;
    int height = rect.bottomRight.y - rect.topLeft.y;
    if (width < 0) width = -width;
    if (height < 0) height = -height;
    return width * height;
}

struct Rectangle rects[10];

int main0() {
    int n = readint();
    int i = 0;
    while (i < n) {
        rects[i].topLeft.x = readint();
        rects[i].topLeft.y = readint();
        rects[i].bottomRight.x = readint();
        rects[i].bottomRight.y = readint();
        i = i + 1;
    }
    
    i = 0;
    while (i < n) {
        int area = calculate_area(rects[i]);
        writeint(area);
        i = i + 1;
    }
    return 0;
}
