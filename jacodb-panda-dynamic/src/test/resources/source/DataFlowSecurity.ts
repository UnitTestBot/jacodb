function source(): number {
    return null
}

function pass(data: boolean) {
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
    // @ts-ignore
    data = pass(data)
    sink(data)
}

function good() {
    let data = source()
    data = validate(data)
    sink(data)
}
