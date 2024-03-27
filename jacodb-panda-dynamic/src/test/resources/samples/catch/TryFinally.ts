let result: string = "";

try {
    const message: string = "An error occurred!";
    throw new Error(message);
} finally {
    result = "Finally block executed.";
}

console.log(result);
