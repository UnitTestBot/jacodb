class Foo {
    constructor() {
        console.log("new Foo");
    }

    foo() {
        Foo.y = 112;
        console.log('print-foo');
    }

    static bar() {
        this.y = 111;
        console.log('print-bar')
    }

    x: number = 99
    static y: number = 109
}
