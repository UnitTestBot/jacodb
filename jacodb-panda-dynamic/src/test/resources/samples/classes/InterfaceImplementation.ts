interface Shape {
    getArea(): number;
}

class Circle implements Shape {
    private readonly radius: number;

    constructor(radius: number) {
        this.radius = radius;
    }

    getArea(): number {
        return Math.PI * this.radius ** 2;
    }
}

const circle = new Circle(5);
console.log("Circle area:", circle.getArea());