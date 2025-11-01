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

// Catch with rethrow: should have CATCH handler
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

// Nested try-catch: inner try-catch inside outer try
function nestedTryCatchInTry(x: number): number {
    try {
        console.log("Outer try");
        try {
            console.log("Inner try");
            if (x < 0) {
                throw new Error("Inner error");
            }
            return x * 2;
        } catch (e) {
            console.log("Inner catch: " + e);
            if (x < -10) {
                throw new Error("Throwing from inner catch");
            }
            return x + 10;
        }
    } catch (e) {
        console.log("Outer catch: " + e);
        return -1;
    }
}

// Nested try-catch: inner try-catch inside catch block
function nestedTryCatchInCatch(x: number): number {
    try {
        if (x < 0) {
            throw new Error("Outer error");
        }
        return x * 2;
    } catch (e) {
        console.log("Outer catch: " + e);
        try {
            console.log("Attempting recovery");
            if (x < -10) {
                throw new Error("Recovery failed");
            }
            return x + 100;
        } catch (innerError) {
            console.log("Inner catch: " + innerError);
            return -999;
        }
    }
}

// Nested try-finally inside try-catch
function nestedTryFinallyInTryCatch(x: number): number {
    try {
        try {
            if (x < 0) {
                throw new Error("Error in nested try");
            }
            return x * 2;
        } finally {
            console.log("Nested finally");
        }
    } catch (e) {
        console.log("Outer catch: " + e);
        return -1;
    }
}
