class Person {
    private readonly name: string;

    constructor(name: string) {
        this.name = name;
    }

    getName(): string {
        return this.name;
    }
}

const obj = new Person("Constructor Example");
console.log(obj.getName());
