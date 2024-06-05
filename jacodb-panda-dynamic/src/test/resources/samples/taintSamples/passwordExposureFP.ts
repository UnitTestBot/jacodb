function getUserData() {}

function getUserPassword() {
    let password = getUserData()
    // validate password
    return password
}

function processUserData(loggingOn) {
    let password = getUserPassword()
    if (loggingOn == 1) {
        console.log(password)
    }
}

// case 1: FP
function usage1() {
    processUserData(0)
}

// case2: no FP
function usage2() {
    processUserData(1)
}


// for debugging (will be deleted soon)

function usage3() {
    let data = getUserData()
    console.log(data)
}

function printOnConsole(data) {
    console.log(data)
}

function usage4() {
    let data = getUserData()
    printOnConsole(data)
}