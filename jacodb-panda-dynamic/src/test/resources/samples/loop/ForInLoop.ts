const person = {
    firstName: "John",
    lastName: "Doe",
    age: 30
};

for (const key in person) {
    console.log(`${key}: ${person[key]}`);
}
