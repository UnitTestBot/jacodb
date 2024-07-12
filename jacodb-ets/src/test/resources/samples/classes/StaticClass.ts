class StaticClass {
    static count: number = 0;

    static incrementCount(): void {
        this.count++;
    }

    static getCount(): number {
        return this.count;
    }
}

StaticClass.incrementCount();
console.log(StaticClass.getCount());
