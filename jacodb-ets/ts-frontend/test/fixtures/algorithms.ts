export function fibonacci(n: number): number {
    if (n <= 1) {
        return n;
    }
    let prev = 0;
    let curr = 1;
    for (let i = 2; i <= n; i++) {
        const next = prev + curr;
        prev = curr;
        curr = next;
    }
    return curr;
}

export function quickSort(arr: number[]): number[] {
    if (arr.length <= 1) {
        return arr;
    }
    const pivot = arr[0];
    const left: number[] = [];
    const right: number[] = [];
    for (let i = 1; i < arr.length; i++) {
        if (arr[i] < pivot) {
            left.push(arr[i]);
        } else {
            right.push(arr[i]);
        }
    }
    return quickSort(left).concat([pivot], quickSort(right));
}

export function wordFrequencies(text: string): Record<string, number> {
    const counts: Record<string, number> = {};
    const words = text.split(" ");
    for (const word of words) {
        const key = word.toLowerCase();
        counts[key] = (counts[key] ?? 0) + 1;
    }
    return counts;
}

export function sumEvens(limit: number): number {
    let total = 0;
    let i = 0;
    while (true) {
        i++;
        if (i > limit) {
            break;
        }
        if (i % 2 !== 0) {
            continue;
        }
        total += i;
    }
    return total;
}

export const pipeline = (values: number[]): number =>
    values
        .map((v) => v * 2)
        .filter((v) => v > 4)
        .reduce((acc, v) => acc + v, 0);
