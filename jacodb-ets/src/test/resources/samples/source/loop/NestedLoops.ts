const colors = ["red", "green", "blue"];
const sizes = ["small", "medium", "large"];

const combinations = [];

for (const color of colors) {
    for (const size of sizes) {
        combinations.push({ color, size });
    }
}

console.log('Color and size combinations:', combinations);
