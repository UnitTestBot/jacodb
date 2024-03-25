function add(a, b) {
    if (typeof a != typeof b)
        throw new Error("Types mismatch!")
    return a + b
}

class Foo {
    x: number

    isSame(y: number): boolean {
        return this.x === y
    }
}

class Bar {
    x: number

    isSame(y: number): boolean {
        return this.x === y
    }
}

function main() {
    let a = new Foo()
    let b = new Bar()

    let c = a.isSame(9) ? b : a;

    a.x = 9

    console.log(add(123, 15))
    console.log(c.isSame(9))
}