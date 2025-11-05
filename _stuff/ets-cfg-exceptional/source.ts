
// Simple try-catch
function testSimpleTryCatch(x: number): number {
    try {
        if (x < 0) {
            throw new Error("Negative value");
        }
        return x * 2;
    } catch (e) {
        return -1;
    }
}

// Try-catch-finally
function testTryCatchFinally(x: number): number {
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
        return result;
    } catch (e) {
        console.log("Error caught: " + e);
        return -1;
    } finally {
        console.log("Finally block executed");
    }
}

// Try-finally without catch
function testTryFinally(x: number): number {
    try {
        return x * 2;
    } finally {
        console.log("Cleanup");
    }
}
