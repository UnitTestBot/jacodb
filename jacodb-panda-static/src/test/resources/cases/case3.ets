class CommonEventPublishData {
    code: int;
    data: string;
}

interface MyError {
    code: int
    name: string
    message: string
    stack?: string
}

class CommonEventManager {
    publish(event: string, options: CommonEventPublishData, callback: (err: MyError) => void) {
        // publish event
    }
}

let commonEventManager = new CommonEventManager();

class CommonSecurity {
    private publish: String = ""

    private publishEventWithData() {
        let options: CommonEventPublishData = {
            code: 1,
            data: "Password123",
        };
        // SINK: send with sensitive data
        commonEventManager.publish("MyCommonEvent", options, (err) => {
            if (err.code) {
                this.publish = "publish event error: " + err.code + ", " + err.message + ", " + err.name + ", " + err.stack;
            } else {
                this.publish = "publish event with data success";
            }
        });
    }
}
