const RESULT = 9

function readInt(data: Message) {
    return RESULT
}

function onRequest(data: Message) {
    let opt = readInt(data);
    for (let i = 0; i < opt; i++) {
        console.log(i);
    }

    return true;
}
