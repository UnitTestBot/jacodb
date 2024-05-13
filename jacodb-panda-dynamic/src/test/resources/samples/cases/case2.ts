const RESULT = 9
const BOOL_RESULT = true

function readInt(data: Message) {
    return RESULT
}

function readBoolean(data: Message) {
    return BOOL_RESULT
}

class PixelMap {}

function createPixelMap(bytes: ArrayBuffer, size: SizeObject): PixelMap {
    return new PixelMap();
}

class Request {
    onRemoteMessageRequest(data: Message): boolean {
        try {
            if (readBoolean(data)) {
                let height = readInt(data);
                let width = readInt(data);
                let bytesNumber = readInt(data);
                let pixelMap = createPixelMap(new ArrayBuffer(bytesNumber), {height: height, width: width});
            }
            return true;
        } catch (e) {
            console.log(e);
        }
        return false;
    }
}
