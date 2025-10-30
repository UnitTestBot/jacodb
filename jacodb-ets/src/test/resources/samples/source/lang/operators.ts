// Test file for operators and expressions

// Arithmetic operators
function testArithmeticOperators(x: number, y: number): number {
    const add = x + y;
    const sub = x - y;
    const mul = x * y;
    const div = x / y;
    const mod = x % y;
    const exp = x ** y;
    return add + sub + mul + div + mod + exp;
}

// Unary operators
function testUnaryOperators(x: number): number {
    const neg = -x;
    const pos = +x;
    let inc = x;
    inc++;
    let dec = x;
    dec--;
    return neg + pos + inc + dec;
}

// Comparison operators
function testComparisonOperators(x: number, y: number): boolean {
    const eq = x === y;
    const neq = x !== y;
    const lt = x < y;
    const lte = x <= y;
    const gt = x > y;
    const gte = x >= y;
    return eq || neq || lt || lte || gt || gte;
}

// Logical operators
function testLogicalOperators(x: boolean, y: boolean): boolean {
    const and = x && y;
    const or = x || y;
    const not = !x;
    return and || or || not;
}

// Bitwise operators
function testBitwiseOperators(x: number, y: number): number {
    const and = x & y;
    const or = x | y;
    const xor = x ^ y;
    const not = ~x;
    const leftShift = x << 2;
    const rightShift = x >> 2;
    const unsignedRightShift = x >>> 2;
    return and | or | xor | not | leftShift | rightShift | unsignedRightShift;
}

// Assignment operators
function testAssignmentOperators(x: number, y: number): number {
    let a = x;
    a += y;
    a -= y;
    a *= y;
    a /= y;
    a %= y;
    a **= 2;
    a &= y;
    a |= y;
    a ^= y;
    a <<= 1;
    a >>= 1;
    a >>>= 1;
    return a;
}

// Null coalescing operator
function testNullCoalescing(x: number | null): number {
    return x ?? 0;
}

// Optional chaining
function testOptionalChaining(obj: any): any {
    return obj?.property?.nested;
}

// Type operators
function testTypeOf(x: any): string {
    return typeof x;
}

function testInstanceOf(x: any): boolean {
    return x instanceof Error;
}

// Spread operator
function testSpreadOperator(arr: number[]): number[] {
    return [...arr, 4, 5, 6];
}

// Destructuring
function testDestructuring(arr: number[]): number {
    const [a, b, ...rest] = arr;
    return a + b + rest.length;
}

// Object destructuring
function testObjectDestructuring(obj: { x: number, y: number }): number {
    const { x, y } = obj;
    return x + y;
}

// Comma operator
function testCommaOperator(x: number): number {
    let a = (x++, x++, x);
    return a;
}

// Grouping operator
function testGrouping(x: number, y: number): number {
    return (x + y) * 2;
}

// Delete operator
function testDelete(obj: any): boolean {
    return delete obj.property;
}

// Void operator
function testVoid(): any {
    return void 0;
}

// In operator
function testInOperator(obj: any): boolean {
    return "property" in obj;
}

// In operator with class
function testInOperatorWithClass(): boolean {
    class Cat {
        constructor(public name: string) {
        }
    }

    let cat = new Cat("Barsik");
    let catHasName = "name" in cat; // true
    let catHasMeow = "meow" in cat; // false
    return catHasName && !catHasMeow;
}

// Scoped variables (shadowing)
function testScopedVariables(): void {
    let x = 42;
    if (true) {
        let x = "kek"; // Shadows outer x
        console.log(x);
    }
    console.log(x);
}

// Conditional (ternary) operator
function testConditional(x: number): string {
    return x > 0 ? "positive" : "negative";
}

// String concatenation
function testStringConcat(s1: string, s2: string): string {
    return s1 + s2;
}

// Template literals
function testTemplateLiterals(x: number, y: number): string {
    return `Sum: ${x + y}`;
}
