// Test file for exception handling constructions

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

// Nested try-catch
// TODO: Uncomment this test when ArkAnalyzer fully supports nested try-catch
// function testNestedTryCatch(x: number): number {
//     try {
//         try {
//             if (x < 0) {
//                 throw new Error("Inner error");
//             }
//             return x;
//         } catch (e) {
//             console.log("Inner catch");
//             throw new Error("Re-throw");
//         }
//     } catch (e) {
//         console.log("Outer catch");
//         return -1;
//     }
// }

// Multiple catch scenarios (different exception types)
function testMultipleCatchPaths(x: number, throwType: string): number {
    try {
        if (throwType === "type1") {
            throw new TypeError("Type error");
        }
        if (throwType === "range") {
            throw new RangeError("Range error");
        }
        if (throwType === "generic") {
            throw new Error("Generic error");
        }
        return x;
    } catch (e) {
        if (e instanceof TypeError) {
            return -1;
        }
        if (e instanceof RangeError) {
            return -2;
        }
        return -3;
    }
}

// Try-catch-finally where finally overrides return value
function testFinallyOverridesReturn(x: number): number {
    try {
        if (x < 0) {
            throw new Error("Negative value");
        }
        return x * 2;  // This return will be overridden
    } catch (e) {
        console.log("Error: " + e);
        return -1;  // This return will also be overridden
    } finally {
        return 42;  // Return in 'finally' overrides everything
    }
}

// Try-catch with multiple exit points
function testMultipleExits(x: number): number {
    try {
        if (x < 0) {
            return -1;
        }
        if (x === 0) {
            throw new Error("Zero not allowed");
        }
        if (x > 100) {
            return 100;
        }
        return x;
    } catch (e) {
        return 0;
    } finally {
        console.log("Done");
    }
}

// TODO: Uncomment this test when ArkAnalyzer fully supports nested exceptions
// Cascading exceptions
// function testCascadingExceptions(): number {
//     try {
//         try {
//             return 0;
//         } catch (e) {
//             return 1;
//         }
//     } catch (e) {
//         return 2;
//     }
// }
