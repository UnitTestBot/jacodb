function foo(a, b) {
    c = a + b + d
    console.log(c)
}

function bar() {
    console.log(d)
}

function baz() {
    k = 10
}

// in this call variables c, d are unresolved
foo(2, 3)

const c = "ccc"
const d = "ddd"

// in this call variable d is already defined
bar()

const k = 11

// in this call const defined variable k is reassigned
baz()