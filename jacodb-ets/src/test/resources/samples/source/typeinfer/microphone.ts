interface Microphone {
    uuid: string
}

class VirtualMicro implements Microphone {
    uuid: string = "virtual_micro_v3"
}

interface Devices {
    microphone: Microphone
}

class VirtualDevices implements Devices {
    microphone: Microphone = new VirtualMicro()
}

function getMicrophoneUuid(device: Devices): string {
    return device.microphone.uuid
}

function entrypoint() {
    let devices = new VirtualDevices()
    let uuid = getMicrophoneUuid(devices)
    console.log(uuid)
}
