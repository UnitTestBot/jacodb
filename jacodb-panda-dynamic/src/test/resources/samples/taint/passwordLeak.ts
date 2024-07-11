// common interface
function getUserData() {}

function getUserLogin() {
    let login = getUserData()
    // login validation
    return login
}

function getUserPassword() {
    let password = getUserData()
    // password validation
    return password
}

// Case 1:
// Counterexample: unencrypted password was leaked in the console
function case1() {
    let login = getUserLogin()
    let password = getUserPassword()
    console.log(login)
    // sensitive information exposure
    console.log(password)
}

// case 2:
// Positive example: unencrypted password was leaked in the console
function encryptPassword(password: String) {
    // password encryption
    return password
}

function case2() {
    let login = getUserLogin()
    let password = getUserPassword()
    let encryptedPassword = encryptPassword(password)
    console.log(login)
    // password was encrypted so there is no exposure
    console.log(encryptedPassword)
}
