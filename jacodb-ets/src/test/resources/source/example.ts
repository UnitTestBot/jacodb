export class Example {
    private readonly message: string;

    constructor(message: string) {
        this.message = message;
    }

    public greet(): string {
        return `Hello, ${this.message}!`;
    }
}

const example = new Example('world');
console.log(example.greet());
