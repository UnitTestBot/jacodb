// Test file for handler classifier tests

// Simple try-catch: should have CATCH handler
function simpleTryCatch(x: number): number {
    try {
        if (x < 0) {
            throw new Error("Negative");
        }
        return x * 2;
    } catch (e) {
        console.log("Error: " + e);
        return -1;
    }
}

// Try-finally: should have COPIED_FINALLY handler
function tryFinally(x: number): number {
    let result = x;
    try {
        result = x * 2;
        return result;
    } finally {
        console.log("Cleanup: " + result);
    }
}

// Try-catch-finally: should have both CATCH and COPIED_FINALLY handlers
function tryCatchFinally(x: number): number {
    let result = 0;
    try {
        if (x < 0) {
            throw new Error("Negative");
        }
        result = x * 2;
        return result;
    } catch (e) {
        console.log("Error: " + e);
        return -1;
    } finally {
        console.log("Finally: " + result);
    }
}

// Catch with rethrow: should have CATCH handler with rethrow pattern
function catchWithRethrow(x: number): number {
    try {
        if (x < 0) {
            throw new Error("Negative");
        }
        return x * 2;
    } catch (e) {
        console.log("Logging error: " + e);
        throw e;  // rethrow the exception
    }
}

// Multiple catches (if supported)
function multipleCatches(x: number): number {
    try {
        if (x < 0) {
            throw new Error("Negative");
        }
        if (x > 100) {
            throw new RangeError("Too large");
        }
        return x * 2;
    } catch (e) {
        console.log("Error: " + e);
        return -1;
    }
}

// Finally that overrides return value
function finallyOverridesReturn(x: number): number {
    try {
        return x * 2;
    } finally {
        return x + 10;  // This overrides the return in try
    }
}

// Complex: try-catch-finally with branches
function complexTryCatchFinally(x: number): number {
    let result = 0;
    try {
        if (x < 0) {
            throw new Error("Negative");
        }
        result = x;
        if (x > 50) {
            result = x * 2;
        } else {
            result = x + 10;
        }
        if (x === 42) {
            return result;
        }
        result = result + 5;
    } catch (e) {
        console.log("Error: " + e);
        if (x < -10) {
            return -999;
        }
        result = -1;
    } finally {
        console.log("Finally: result = " + result);
        result = result + 1;
    }
    return result;
}
