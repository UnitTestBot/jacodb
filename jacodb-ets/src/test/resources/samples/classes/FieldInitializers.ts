class Foo {
    x: number = 99

    static y: number = 111

    constructor() {
        console.log("inside Foo::constructor");
    }

    foo() {
        Foo.y = 222;
        console.log('inside Foo::foo');
    }

    static bar() {
        this.y = 333;
        console.log('inside Foo::bar')
    }
}
