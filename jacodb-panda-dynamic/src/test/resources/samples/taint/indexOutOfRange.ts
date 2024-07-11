function readUInt() {}

function verifyIndex(arr, index) {
    if (index < 0 || index >= arr.length) {
        throw new Error("Index out of range!")
    }
}

function getElement(arr, index) {
    return arr[index];
}

function safeGetElement(arr, index) {
    verifyIndex(arr, index)
    return arr[index];
}

function main() {
    let arr = [1, 2, 3, 4, 5]
    let index = readUInt() % arr.length
    console.log(getElement(arr, index))
    console.log(safeGetElement(arr, index))
}
