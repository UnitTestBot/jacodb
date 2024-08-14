class Cat {
    constructor(public name: string) {
    }
}

let cat = new Cat("Barsik");
let catHasName = "name" in cat; // true
let catHasMeow = "meow" in cat; // false
