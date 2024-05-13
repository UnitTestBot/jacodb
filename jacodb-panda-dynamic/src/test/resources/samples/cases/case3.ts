interface MyError {
    code: int
    name: string
    message: string
    stack?: string
}

function publishEvent(event: string, password: String, callback: (err: MyError) => void) {
    // publish event
}

function getPassword() {
    return "Password123"
}

class CommonSecurity {
    private publish: String = ""

    private publishEventWithData() {
        let password = getPassword()
        // SINK: send with sensitive data
        publishEvent("MyCommonEvent", password, (err) => {
            if (err.code) {
                this.publish = "publish event error: " + err.code + ", " + err.message + ", " + err.name + ", " + err.stack;
            } else {
                this.publish = "publish event with data success";
            }
        });
    }
}
