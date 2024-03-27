class ParentClass {
    protected name: string;

    constructor(name: string) {
        this.name = name;
    }

    getName(): string {
        return this.name;
    }
}

class ChildClass extends ParentClass {
    private readonly age: number;

    constructor(name: string, age: number) {
        super(name);
        this.age = age;
    }

    getAge(): number {
        return this.age;
    }
}

const childObj = new ChildClass("Child Example", 25);
console.log(childObj.getName());
console.log(childObj.getAge());
