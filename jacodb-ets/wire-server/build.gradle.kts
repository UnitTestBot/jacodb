import com.squareup.wire.kotlin.grpcserver.GrpcServerSchemaHandler

plugins {
    id(Plugins.Wire)
}

buildscript {
    dependencies {
        classpath(Libs.wire_grpc_server_generator)
    }
}

dependencies {
    protoSource(project(":jacodb-ets:wire-protos"))
    api(Libs.wire_grpc_server)
    api(Libs.grpc_api)
    implementation(Libs.grpc_protobuf)
    implementation(Libs.grpc_services) // for ProtoReflectionService
    implementation(Libs.kotlin_logging)
    runtimeOnly(Libs.wire_runtime)
    runtimeOnly(Libs.grpc_netty_shaded)
}

wire {
    custom {
        schemaHandlerFactory = GrpcServerSchemaHandler.Factory()
        options = mapOf(
            "rpcCallStyle" to "blocking",
            "singleMethodServices" to "false",
        )
        exclusive = false
    }
    kotlin {
        rpcRole = "server"
        rpcCallStyle = "blocking"
        singleMethodServices = false
    }
}
