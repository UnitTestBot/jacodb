// Test file for various loop constructions

// Simple for loop
function testSimpleForLoop(n: number): number {
    let sum = 0;
    for (let i = 0; i < n; i++) {
        sum += i;
    }
    return sum;
}

// For loop with break
function testForLoopWithBreak(n: number): number {
    let sum = 0;
    for (let i = 0; i < n; i++) {
        if (i === 10) {
            break;
        }
        sum += i;
    }
    return sum;
}

// For loop with continue
function testForLoopWithContinue(n: number): number {
    let sum = 0;
    for (let i = 0; i < n; i++) {
        if (i % 2 === 0) {
            continue;
        }
        sum += i;
    }
    return sum;
}

// While loop
function testWhileLoop(n: number): number {
    let sum = 0;
    let i = 0;
    while (i < n) {
        sum += i;
        i++;
    }
    return sum;
}

// While loop with break
function testWhileLoopWithBreak(n: number): number {
    let sum = 0;
    let i = 0;
    while (true) {
        if (i >= n) {
            break;
        }
        sum += i;
        i++;
    }
    return sum;
}

// Do-while loop
function testDoWhileLoop(n: number): number {
    let sum = 0;
    let i = 0;
    do {
        sum += i;
        i++;
    } while (i < n);
    return sum;
}

// Nested loops
function testNestedLoops(rows: number, cols: number): number {
    let sum = 0;
    for (let i = 0; i < rows; i++) {
        for (let j = 0; j < cols; j++) {
            sum += i * cols + j;
        }
    }
    return sum;
}

// For-in loop
function testForInLoop(obj: any): number {
    let count = 0;
    for (let key in obj) {
        count++;
    }
    return count;
}

// For-of loop
function testForOfLoop(arr: number[]): number {
    let sum = 0;
    for (let value of arr) {
        sum += value;
    }
    return sum;
}

// Loop with labeled break
function testLabeledBreak(n: number): number {
    let result = 0;
    outer: for (let i = 0; i < n; i++) {
        for (let j = 0; j < n; j++) {
            if (i * j > 100) {
                break outer;
            }
            result += i * j;
        }
    }
    return result;
}

// Loop with labeled continue
function testLabeledContinue(n: number): number {
    let result = 0;
    outer: for (let i = 0; i < n; i++) {
        for (let j = 0; j < n; j++) {
            if (j % 2 === 0) {
                continue outer;
            }
            result += i * j;
        }
    }
    return result;
}

// Infinite loop with conditional break
function testInfiniteLoop(n: number): number {
    let i = 0;
    let sum = 0;
    while (true) {
        sum += i;
        i++;
        if (i >= n) {
            break;
        }
    }
    return sum;
}
