plugins {
    id(Plugins.Wire)
}

dependencies {
    protoSource(project(":jacodb-ets:wire-protos"))
    api(Libs.wire_grpc_client)
}

wire {
    kotlin {
        rpcRole = "client"
    }
}
