function multiplyMatrices(A: number[][], B: number[][]): number[][] {
    const rowsA = A.length;
    const colsA = A[0].length;
    const rowsB = B.length;
    const colsB = B[0].length;
    // @ts-ignore
    const result = Array.from({ length: rowsA }, () => new Array(colsB).fill(0));

    if (colsA !== rowsB) {
        throw new Error('Columns of A must match rows of B');
    }

    for (let i = 0; i < rowsA; i++) {
        for (let j = 0; j < colsB; j++) {
            for (let k = 0; k < colsA; k++) {
                result[i][j] += A[i][k] * B[k][j];
            }
        }
    }

    return result;
}

const A = [
    [1, 2],
    [3, 4]
];

const B = [
    [2, 0],
    [1, 2]
];

const product = multiplyMatrices(A, B);
console.log('Product of matrices A and B is:', product);
