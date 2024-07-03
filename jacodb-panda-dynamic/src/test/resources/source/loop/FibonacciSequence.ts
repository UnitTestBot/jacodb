function generateFibonacci(n: number): number[] {
    const fib = [0, 1];
    for (let i = 2; i < n; i++) {
        fib[i] = fib[i - 1] + fib[i - 2];
    }
    return fib;
}

const fibonacciSeries = generateFibonacci(10);
console.log('Fibonacci Series:', fibonacciSeries);
