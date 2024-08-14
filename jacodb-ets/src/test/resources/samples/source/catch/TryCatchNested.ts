let outerResult: string = "";
let innerResult: string = "";

try {
    try {
        const innerMessage: string = "An error occurred in inner try block!";
        throw new Error(innerMessage);
    } catch (innerError) {
        innerResult = "Caught an error in inner catch block: " + innerError.message;
    }
} catch (outerError) {
    outerResult = "Caught an error in outer catch block: " + outerError.message;
}

console.log(outerResult);
console.log(innerResult);
