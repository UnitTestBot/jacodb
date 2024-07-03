function foo(i: number) {
    var index = i
    while (index < 10) {
        if (index < 5) {
            index = 10
        } else {
            index = 2
        }
    }
    return 0
}
