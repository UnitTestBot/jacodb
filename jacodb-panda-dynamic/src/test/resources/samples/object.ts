function main() {
    const b = 2;
    const c = { c1: 3, c2: 4 };

    const obj = {
        a: 1,
        b,
        ...c,
        method() {
            console.log('method');
        },
        _value: 0,
        get accessor() {
            return 'getter';
        },
        set accessor(value) {
            console.log('setter', value);
        }
    };

    console.log(obj);
    obj.method();
    console.log(obj.accessor);
    obj.accessor = 'new value';
    console.log(obj.accessor);
}

main();
