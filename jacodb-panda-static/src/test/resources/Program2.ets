type MaybeString = String | null

function source(): MaybeString {
    return null
}

function getNull(): MaybeString {
    return null
}

function pass(data: MaybeString) {
    return data
}

function validate(data: MaybeString): MaybeString {
    if (data == null) return "OK"
    return data
}

function sink(data: MaybeString) {
    if (data == null) throw new Error("Error!")
}

function bad() {
    let data = source()
    sink(pass(data))
}

function good() {
    let data = source()
    sink(validate(data))
}
