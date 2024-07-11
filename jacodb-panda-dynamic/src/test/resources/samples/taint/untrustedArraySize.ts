function getNumber() {}

function getSquares(n) {
    let arr = new Array(n);
    for (let index = 0; index < n; index++) {
        arr[index] = index * index;
    }
    return arr
}

function main() {
    let n = getNumber()
    let arr = getSquares(n)
}
