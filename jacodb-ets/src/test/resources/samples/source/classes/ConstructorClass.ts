class ConstructorClass {
    private readonly name: string;

    constructor(name: string) {
        this.name = name;
    }

    getName(): string {
        return this.name;
    }
}

const constructorObj = new ConstructorClass("Constructor Example");
console.log(constructorObj.getName());
