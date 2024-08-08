function add(a, b) {
    if (typeof a != typeof b) throw new Error("Types mismatch!")
    return a + b
}

function main() {
    console.log(add(123, 15))
}
