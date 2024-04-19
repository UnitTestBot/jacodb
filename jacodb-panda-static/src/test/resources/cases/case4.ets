interface Context {
}

declare function getContext(): Context;

class AtManager {
}

class AbilityAccessCtrl {
    createAtManager(): AtManager {
        return {};
    }
}

let abilityAccessCtrl = new AbilityAccessCtrl();

type Permissions = string;

class PermissionUtil {
    static requestPermissionsFromUser(permissions: Permissions[]): boolean {
        return true;
    }
}

class Settings {
    setValue(context: Context, key: string, value: string) {
    }

    getValue(context: Context, key: string): string {
        return "";
    }
}

let settings = new Settings();

class Pasteboard {
    clear() {
        //
    }

    setPasteData(pasteData: string) {
        //
    }
}

// From 'pasteboard' module.
function getSystemPasteboard(): Pasteboard {
    return new Pasteboard();
}

// From 'pasteboard' module.
function createPlainTextData(data: string): string {
    return data;
}

class Setting {
    private context: Context = getContext();

    setData() {
        let atManager = abilityAccessCtrl.createAtManager();
        let message = "helloWorld";
        let password = "Password123";
        try {
            // Verify whether the application is granted setting permission
            const permissions: Permissions[] = ['ohos.permission.MANAGE_SECURE_SETTINGS'];
            if (PermissionUtil.requestPermissionsFromUser(permissions)) {
                settings.setValue(this.context, 'message', message);

                // SINK: The application stores the password of sensitive information in the settings database
                settings.setValue(this.context, "password", password)
            } else {
                // Unauthorized
            }
        } catch (err) {
            return;
        }
    }

    // SINK: Any application can directly read the data of the settings database, causing information leakage
    getData() {
        let value = settings.getValue(this.context, "password")
        console.log("get settings customs data password: " + value)
    }

    // It is forbidden to store severity level data in the clipboard
    pasteboard() {
        // Counterexample: Text copy
        console.log('Get SystemPasteboard')
        let systemPasteboard = getSystemPasteboard()
        systemPasteboard.clear()

        let password = 'Password123'
        console.log('createPlainTextData = ' + password)
        let pasteData = createPlainTextData(password)

        console.log('Writes PasteData to the pasteboard')
        systemPasteboard.setPasteData(pasteData)
    }
}
