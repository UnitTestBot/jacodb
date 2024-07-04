function foo(a, b) {
    c = a + b + d
    console.log(c)
}

function bar() {
    c = "bar function call"
    console.log(c)
}

// in this call variables c, d are unresolved
foo(2, 3)

let c = 5
let d = 5

// in this call variable c is already defined
bar()

// in this call variables c, d already defined
foo(2, 3)
