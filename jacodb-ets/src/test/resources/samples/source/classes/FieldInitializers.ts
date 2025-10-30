class Foo {
    static y: number = 111;
    x: number = 99;

    constructor() {
        console.log("inside Foo::constructor");
    }

    static bar() {
        this.y = 333;
        console.log('inside Foo::bar');
    }

    foo() {
        Foo.y = 222;
        console.log('inside Foo::foo');
    }
}
