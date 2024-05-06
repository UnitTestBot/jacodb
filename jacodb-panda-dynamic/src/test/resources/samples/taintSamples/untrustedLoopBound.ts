function getUserData() {}

// Counterexamples with untrusted loop bound
function doWhileLoop() {
    let count = getUserData()
    let index = 0
    do {
        console.log(`Index is ${index}`);
        index++;
    } while (index < count);

}

function forLoop() {
    let count = getUserData()
    for (let index = 0; index < count; index++) {
        console.log(`Index is ${index}`);
    }
}

function whileLoop() {
    let count = getUserData()
    let index = 0
    while (index < count) {
        console.log(`Index is ${index}`);
        index++;
    }
}
