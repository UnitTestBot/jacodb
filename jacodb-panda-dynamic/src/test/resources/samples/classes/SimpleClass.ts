class SimpleClass {
    private name: string;

    setName(name: string): void {
        this.name = name;
    }

    getName(): string {
        return this.name;
    }
}

const simpleObj = new SimpleClass();
simpleObj.setName("Example");
console.log(simpleObj.getName());
