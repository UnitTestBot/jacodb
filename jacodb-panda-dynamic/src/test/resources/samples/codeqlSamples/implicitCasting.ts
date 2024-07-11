function primitiveLiterals() {
    console.log('5' + 3);       // "53" (Concatenation of string '5' and number 3)
    console.log('5' - 3);       // 2 (Subtraction of string '5' (converted to number 5) from 3)
    console.log('5' * 3);       // 15 (Multiplication of string '5' (converted to number 5) by 3)
    console.log('5' / 2);       // 2.5 (Division of string '5' (converted to number 5) by 2)
    console.log(true + 1);      // 2 (Addition of true (converted to number 1) and 1)
    console.log(false + 1);     // 1 (Addition of false (converted to number 0) and 1)
    console.log(null + 1);      // 1 (Addition of null (converted to number 0) and 1)
    console.log(undefined + 1); // NaN (Addition of undefined (cannot be converted to number) and 1)
    console.log('5' == 5);      // true (Loose equality check between string '5' (converted to number 5) and number 5)
    console.log(null == undefined); // true (Loose equality check between null and undefined)
}

function complexLiterals() {
    console.log([] + 1);        // "1" (Concatenation of an empty array (converted to an empty string) and number 1)
    console.log([1] + 1);       // "11" (Concatenation of an array with one element [1] (converted to the string "1") and number 1)
    console.log([1, 2] + 1);    // "1,21" (Concatenation of an array [1, 2] (converted to the string "1,2") and number 1)
    console.log({} + 1);        // "[object Object]1" (Concatenation of an empty object (converted to the string "[object Object]") and number 1)
}

function binaryOperationsWithStrings() {
    console.log('alien' + 3);       // "alien3"
    console.log('alien' - 3);       // NaN
    console.log('alien' * 3);       // NaN
    console.log('alien' ** 3);       // NaN
    console.log('alien' / 3);       // NaN
    console.log('alien' / 'lone');       // NaN
    console.log('alien' * 'lone');       // NaN
    console.log('alien' + 'lone');       // alienlone
    console.log('alien' - 'lone');       // NaN
    console.log('999' - '666');       // 333 as number
    console.log('999' * '3');       // 333 as number
    console.log(3 ** '3');          // 27
    console.log('21' * 'true')      // NaN
}

function binaryOperationsWithStrings2() {
    console.log('21' * true)        // 21
    let num = 9
    let notNumString = 'Number'
    let numString = '9'
    console.log(notNumString + numString)       // Number9
    console.log(notNumString + numString)       // NaN
    console.log(num * numString)       // 81
    console.log(notNumString * num)       // NaN
    console.log(notNumString + num)       // Number9
}
