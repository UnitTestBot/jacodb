interface Message {
    readInt(): number;
}

function onRequest(data: Message) {
    let opt = data.readInt();

    for (let i = 0; i < opt; i++) {
        console.log(i);
    }

    return true;
}
