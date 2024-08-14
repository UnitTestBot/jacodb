let result1: string = "";
let result2: string = "";

try {
    const message1: string = "An error occurred in try block 1!";
    throw new Error(message1);
} catch (error) {
    result1 = "Caught an error in catch block 1: " + error.message;
}

try {
    const message2: string = "An error occurred in try block 2!";
    throw new Error(message2);
} catch (error) {
    result2 = "Caught an error in catch block 2: " + error.message;
}

console.log(result1);
console.log(result2);
