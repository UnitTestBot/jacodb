function source(): number | null {
    return null;
}

function pass(data: number | null): number | null {
    return data;
}

function validate(data: number | null): number {
    if (data == null) {
        return 0;
    }
    return data;
}

function sink(data: number | null) {
    if (data == null) {
        throw new Error("Error!");
    }
}

function bad() {
    let data = source();
    data = pass(data);
    sink(data);
}

function good() {
    let data = source();
    data = validate(data);
    sink(data);
}
