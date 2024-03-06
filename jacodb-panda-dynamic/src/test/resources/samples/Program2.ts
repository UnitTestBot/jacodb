function source() {
    return null
}

function pass(data) {
    return data
}

function validate(data) {
    if (data == null) return "OK"
    return data
}

function sink(data) {
    if (data == null) throw new Error("Error!")
}

function bad() {
    let data = source()
    data = pass(data)
    sink(data)
}

function good() {
    let data = source()
    data = validate(data)
    sink(data)
}
