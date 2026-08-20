export namespace Geometry {
    export class Point {
        constructor(
            public x: number = 0,
            public y: number = 0,
        ) {}

        distanceTo(other: Point): number {
            const dx = this.x - other.x;
            const dy = this.y - other.y;
            return Math.sqrt(dx * dx + dy * dy);
        }
    }

    export namespace Shapes {
        export abstract class Shape {
            abstract area(): number;

            describe(): string {
                return `area = ${this.area()}`;
            }
        }

        export class Rect extends Shape {
            constructor(
                private readonly w: number,
                private readonly h: number,
            ) {
                super();
            }

            area(): number {
                return this.w * this.h;
            }
        }
    }
}

const origin = new Geometry.Point();
const p = new Geometry.Point(3, 4);
const { x, y } = p;
console.log(x, y, origin.distanceTo(p));

const sizes = [1, 2, 3].map((n) => {
    const rect = new Geometry.Shapes.Rect(n, n + 1);
    return rect.area();
});
let biggest = 0;
for (const [index, size] of sizes.entries()) {
    if (size > biggest) {
        biggest = size;
    }
    console.log(`#${index}: ${size}`);
}
