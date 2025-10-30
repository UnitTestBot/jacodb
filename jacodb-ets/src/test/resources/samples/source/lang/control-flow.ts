// Test file for various control flow constructions

// Simple if-else
function testIfElse(x: number): number {
    if (x > 0) {
        return 1;
    } else {
        return -1;
    }
}

// If-else-if chain
function testIfElseIfChain(x: number): string {
    if (x < 0) {
        return "negative";
    } else if (x === 0) {
        return "zero";
    } else if (x < 10) {
        return "small";
    } else {
        return "large";
    }
}

// Nested if statements
function testNestedIf(x: number, y: number): boolean {
    if (x > 0) {
        if (y > 0) {
            return true;
        } else {
            return false;
        }
    } else {
        if (y > 0) {
            return false;
        } else {
            return true;
        }
    }
}

// Switch statement with multiple cases
function testSwitch(x: number): string {
    switch (x) {
        case 1:
            return "one";
        case 2:
            return "two";
        case 3:
        case 4:
            return "three or four";
        default:
            return "other";
    }
}

// Switch with fallthrough
function testSwitchFallthrough(x: number): number {
    let result = 0;
    switch (x) {
        case 1:
            result += 1;
        case 2:
            result += 2;
        case 3:
            result += 3;
            break;
        default:
            result = -1;
    }
    return result;
}

// Ternary operator
function testTernary(x: number): string {
    return x > 0 ? "positive" : "non-positive";
}

// Nested ternary
function testNestedTernary(x: number): string {
    return x > 0 ? "positive" : x < 0 ? "negative" : "zero";
}

// Early return
function testEarlyReturn(x: number): number {
    if (x < 0) {
        return -1;
    }
    if (x === 0) {
        return 0;
    }
    return x * 2;
}

// Multiple returns in branches
function testMultipleReturns(x: number, y: number): number {
    if (x > y) {
        return x;
    }
    if (y > x) {
        return y;
    }
    return 0;
}
