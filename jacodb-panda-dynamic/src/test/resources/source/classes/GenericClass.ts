class GenericClass<T> {
    private readonly data: T;

    constructor(data: T) {
        this.data = data;
    }

    getData(): T {
        return this.data;
    }
}

const stringObj = new GenericClass<string>("String Example");
console.log(stringObj.getData());

const numberObj = new GenericClass<number>(100);
console.log(numberObj.getData());
