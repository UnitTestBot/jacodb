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
    try {
        sink(data);
    } catch (e: Error) {
        sink("data");
    }
}

function good() {
    let data = source()
    try {
        sink("data")
        throw new Error("errorerrorerror");
    } catch (e: Error) {
        sink(data);
        console.log(e);
    }
}
