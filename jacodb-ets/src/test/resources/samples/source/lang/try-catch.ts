function testTryCatch(x: number): number {
    let result = 0;

    try {
        if (x < 0) {
            throw new Error("Negative value");
        }
        result = x * 2;
        if (x > 100) {
            throw new Error("Value too large");
        }
        result = result + 10;
    } catch (e) {
        console.log("Error caught: " + e);
        result = -1;
    } finally {
        console.log("Finally block executed");
        result = result + 1;
    }

    return result;
}

// Test different branches
testTryCatch(5);    // Normal path
testTryCatch(-3);   // Error path (negative)
testTryCatch(150);  // Error path (too large)
