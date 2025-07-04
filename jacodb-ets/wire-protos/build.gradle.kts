plugins {
    id(Plugins.Wire)
}

wire {
    protoLibrary = true
    kotlin {
        rpcRole = "none"
    }
}
