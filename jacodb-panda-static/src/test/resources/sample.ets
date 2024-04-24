function aplusb(a: int, b: int) : int {
    return a + b;
}

function aorb(a: int, b: int) : int {
    if (a == 0)
        return a;
    else
        return a * b;
}

interface I1 {
    foo(a: int): int
}
interface I2 {}

abstract class A0 {
    abstract greet(c: int): void
}

class P<T> {
    t: T
}

class A extends A0 implements I1, I2 {
    a: int
    b: String

    override foo(a: int): int {
        let b = 0;
        if (a > 0) {
            b = a;
        } else if (a < 1) {
            b = aorb(a, 4);
        }
        return b;
    }

    override greet(c: int): void {
        let d = 0;
        try {
            if (c > 0) {
                d = this.a / c;
                console.print(this.b + c);
            } else if (c < 0) {
                console.print("lapki");
            } else {
                d = 5;
                throw new IllegalStateException("a gde");
            }
        } catch (e) {
            this.a = d;
            console.log("catch");
        } finally {
            console.log("and finally");
            throw new Error("throw error");
        }
    }
}

class ABVGDEJOZH {
    kek: int
}

class A1 extends A {
    c: String
}

class B extends ABVGDEJOZH {
    constructor(a: int) {
        this.kek = a;
    }

    transform(obj: I1): int {
        return obj.foo(this.kek);
    }
}

function main(): void {
    let a: int = 353;
    if (aplusb(2, 3) < 0)
        a = 11;
    console.print(a == 1);
    console.print(aplusb(2, 3));
    let obj = new A();
    obj.greet(42);
    obj.foo(5);
    aorb(1, 0);

    let b = new B(4);
    console.print(b.transform(obj));

    let p = new P<int>();
}
