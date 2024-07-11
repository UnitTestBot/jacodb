function add(a: number, b: number) {
    return a + b
}

function foo() {
    let bad1 = add()
    let bad2 = add(5)
    let good = add(5, 7)
}

function add1(a: number, b: number = 11) {
    return a + b;
}

function foo1() {
    let bad = add1()
    let good1 = add1(2)
    let good2 = add1(2, 3)
}

function add2(a: number = 22, b: number = 33) {
    return a + b;
}

function foo2() {
    let good1 = add2()
    let good2 = add2(2)
    let good3 = add2(2, 3)
}

class User {
    private name: String

    getName() {
        return this.name
    }

    setName(newName: String) {
        this.name = newName
    }
}

function rightUsage() {
    let user = new User()
    user.setName("")
    console.log(user.getName())
}

function wrongUsage() {
    let user = new User("Walter")
    console.log(user.getName("Walter"))
    user.setName()
}
