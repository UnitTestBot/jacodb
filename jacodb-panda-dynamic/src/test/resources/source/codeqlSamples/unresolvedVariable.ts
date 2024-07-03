function foo(a, b) {
    console.log("foo")
    console.log(a + b + c)
}

function bar() {
    console.log("bar")
    console.lod(d)
}

function baz() {
    console.log("baz")
    console.log(s)
}

// in this call variable c is unresolved
foo(2, 3)
let c = 5
// in this call variable c already defined
foo(2, 3)

let d = 1
// variable d in defined in this call
bar()

if (d == 2) {
    let s = "555"
    console.log("s: " + s)
}
else {
    // in both calls s is unresolved
    console.log(s)
    baz()
}

// s could be unresolved (so in common it unresolved)
console.log(s)