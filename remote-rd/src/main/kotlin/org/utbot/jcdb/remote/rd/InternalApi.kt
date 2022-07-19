package org.utbot.jcdb.remote.rd

import com.jetbrains.rd.framework.*
import kotlin.reflect.KClass

val serializers = Serializers().also {
    it.register(GetClasspathReq)
    it.register(GetClassReq)
    it.register(GetClassRes)
}


class GetClasspathReq(val locations: List<String>) {

    companion object : IMarshaller<GetClasspathReq> {

        override val _type: KClass<GetClasspathReq> = GetClasspathReq::class

        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): GetClasspathReq {
            return GetClasspathReq(
                buffer.readArray {
                    buffer.readString()
                }.toList()
            )
        }

        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: GetClasspathReq) {
            buffer.writeArray(value.locations.toTypedArray()) {
                buffer.writeString(it)
            }
        }
    }
}

class GetClassReq(val cpKey: String, val className: String) {

    companion object : IMarshaller<GetClassReq> {

        override val _type: KClass<GetClassReq> = GetClassReq::class

        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): GetClassReq {
            return GetClassReq(
                buffer.readString(),
                buffer.readString()
            )
        }

        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: GetClassReq) {
            buffer.writeString(value.cpKey)
            buffer.writeString(value.className)
        }
    }
}

class GetClassRes(val bytes: ByteArray) {

    companion object : IMarshaller<GetClassRes> {

        override val _type: KClass<GetClassRes> = GetClassRes::class

        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): GetClassRes {
            return GetClassRes(
                buffer.readByteArray()
            )
        }

        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: GetClassRes) {
            buffer.writeByteArray(value.bytes)
        }
    }
}