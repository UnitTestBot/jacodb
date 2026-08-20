function createEmitter() {
    const handlers = {};
    return {
        on(name, handler) {
            const list = handlers[name] ?? [];
            list.push(handler);
            handlers[name] = list;
        },
        emit(name, payload) {
            const list = handlers[name];
            if (!list) {
                return 0;
            }
            let delivered = 0;
            for (const handler of list) {
                try {
                    handler(payload);
                    delivered++;
                } catch (err) {
                    console.error("handler failed", err);
                }
            }
            return delivered;
        },
    };
}

function main() {
    const emitter = createEmitter();
    let total = 0;
    emitter.on("tick", function (n) {
        total += n;
    });
    for (let i = 0; i < 10; i++) {
        emitter.emit("tick", i);
    }
    const summary = "total: " + total;
    console.log(summary);
    return total;
}

main();
