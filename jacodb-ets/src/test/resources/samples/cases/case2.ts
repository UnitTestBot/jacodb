interface Message {
    readInt(): number;

    readBoolean(): boolean;
}

class PixelMap {
}

class Size {
    height: number
    width: number
}

function createPixelMap(bytes: ArrayBuffer, size: Size): PixelMap {
    return new PixelMap();
}

class Request {
    onRemoteMessageRequest(data: Message): boolean {
        try {
            if (data.readBoolean()) {
                let height = data.readInt();
                let width = data.readInt();
                let bytesNumber = data.readInt();
                let pixelMap = createPixelMap(new ArrayBuffer(bytesNumber), {height: height, width: width});
            }
            return true;
        } catch (e) {
            console.log(e);
        }
        return false;
    }
}
