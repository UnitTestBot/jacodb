// Test file for function-related constructions

// Simple function
function simpleFunction(x: number): number {
    return x * 2;
}

// Function with multiple parameters
function multiParamFunction(x: number, y: number, z: number): number {
    return x + y + z;
}

// Function with default parameters
function defaultParamFunction(x: number, y: number = 10): number {
    return x + y;
}

// Function with rest parameters (varargs)
function restParamFunction(base: number, ...numbers: number[]): number {
    let sum = base;
    for (let n of numbers) {
        sum += n;
    }
    return sum;
}

// Function with optional parameters
function optionalParamFunction(x: number, y?: number): number {
    return y !== undefined ? x + y : x;
}

// Arrow function
const arrowFunction = (x: number): number => {
    return x * 2;
};

// Arrow function with implicit return
const arrowFunctionImplicit = (x: number): number => x * 2;

// Function returning function (closure)
function outerFunction(x: number): (y: number) => number {
    return function(y: number): number {
        return x + y;
    };
}

// Function with function parameter
function higherOrderFunction(x: number, fn: (n: number) => number): number {
    return fn(x);
}

// Recursive function
function factorial(n: number): number {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

// Mutually recursive functions
function isEven(n: number): boolean {
    if (n === 0) {
        return true;
    }
    return isOdd(n - 1);
}

function isOdd(n: number): boolean {
    if (n === 0) {
        return false;
    }
    return isEven(n - 1);
}

// Function with destructuring parameters
function destructuringParams({x, y}: {x: number, y: number}): number {
    return x + y;
}

// Generator function
function* generatorFunction(n: number) {
    for (let i = 0; i < n; i++) {
        yield i;
    }
}

// Async function
async function asyncFunction(x: number): Promise<number> {
    return x * 2;
}

// Async function with await
async function asyncWithAwait(x: number): Promise<number> {
    const result = await asyncFunction(x);
    return result + 1;
}

// IIFE (Immediately Invoked Function Expression)
const iifeResult = (function(x: number): number {
    return x * 2;
})(42);

// Function overloading (TypeScript)
function overloadedFunction(x: number): number;
function overloadedFunction(x: string): string;
function overloadedFunction(x: any): any {
    if (typeof x === "number") {
        return x * 2;
    }
    return x + x;
}

// Function with void return
function voidFunction(x: number): void {
    console.log(x);
}

// Function with never return (throws)
function neverFunction(msg: string): never {
    throw new Error(msg);
}
