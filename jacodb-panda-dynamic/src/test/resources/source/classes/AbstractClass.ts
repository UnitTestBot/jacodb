abstract class Shape {
    abstract getArea(): number;
}

class Rectangle extends Shape {
    width: number;
    height: number;

    constructor(width: number, height: number) {
        super();
        this.width = width;
        this.height = height;
    }

    getArea(): number {
        return this.width * this.height;
    }
}

const rectangle = new Rectangle(5, 10);
console.log(rectangle.getArea());
