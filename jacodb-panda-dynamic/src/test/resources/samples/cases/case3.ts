interface MyError {
    code: int
    name: string
    message: string
    stack?: string
}

class CommonEventManager {
    publish(event: string, options: CommonEventPublishDataObject, callback: (err: MyError) => void) {
        // publish event
    }
}

let commonEventManager = new CommonEventManager();

class CommonSecurity {
    private publish: String = ""

    private getPassword() {
        return "Password123"
    }

    private publishEventWithData() {
        let password = this.getPassword()
        let code = 1
        let options = {
            code: code,
            data: password,
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
