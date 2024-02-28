class Sample2 {
    source(): string {
        return null
    }

    pass(data) {
        return data
    }

    validate(data) {
        if (data == null) return "OK"
        return data
    }

    sink(data) {
        if (data == null) throw Error("Error!")
    }

    bad() {
        let data = this.source()
        data = this.pass(data)
        this.sink(data)
    }

    good() {
        let data = this.source()
        data = this.validate(data)
        this.sink(data)
    }
}
