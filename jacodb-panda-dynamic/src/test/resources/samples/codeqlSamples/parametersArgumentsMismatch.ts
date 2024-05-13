function add(a: number, b: number) {
    return a + b
}

function foo() {
    let bad = add(5)
    let good = add(5, 7)
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
