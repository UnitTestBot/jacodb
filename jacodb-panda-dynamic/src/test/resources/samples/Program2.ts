class Sample2 {

    goodSource() {
        return 10
    }

    badSource() {
        return null
    }

    passThrough(data) {
        let smth = data + 5
    }

    sink(data) {
        if (data == null) throw Error("Error!")
    }

    good() {
        let data = this.goodSource()
        this.passThrough(data)
        this.sink(data)
    }

    bad() {
        let data = this.badSource()
        this.passThrough(data)
        this.sink(data)
    }
}
