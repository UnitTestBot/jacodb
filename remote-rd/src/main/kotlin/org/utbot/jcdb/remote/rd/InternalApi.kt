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

abstract class ClasspathBasedReq(val cpKey: String)

open class GetClassReq(cpKey: String, val className: String) : ClasspathBasedReq(cpKey) {

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

class GetClassRes(val location: String?, val serializedClassInfo: ByteArray) {

    companion object : IMarshaller<GetClassRes> {

        override val _type: KClass<GetClassRes> = GetClassRes::class

        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): GetClassRes {
            return GetClassRes(
                buffer.readNullableString(),
                buffer.readByteArray()
            )
        }

        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: GetClassRes) {
            buffer.writeNullableString(value.location)
            buffer.writeByteArray(value.serializedClassInfo)
        }
    }
}

class GetSubClassesReq(cpKey: String, className: String, val allHierarchy: Boolean) :
    GetClassReq(cpKey, className) {

    companion object : IMarshaller<GetSubClassesReq> {

        override val _type: KClass<GetSubClassesReq> = GetSubClassesReq::class

        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): GetSubClassesReq {
            return GetSubClassesReq(
                buffer.readString(),
                buffer.readString(),
                buffer.readBoolean()
            )
        }

        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: GetSubClassesReq) {
            buffer.writeString(value.cpKey)
            buffer.writeString(value.className)
            buffer.writeBoolean(value.allHierarchy)
        }
    }
}

class GetSubClassesRes(val classes: List<GetClassRes>) {

    companion object : IMarshaller<GetSubClassesRes> {

        override val _type: KClass<GetSubClassesRes> = GetSubClassesRes::class

        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): GetSubClassesRes {
            return GetSubClassesRes(
                buffer.readArray {
                    GetClassRes.read(ctx, buffer)
                }.toList()
            )
        }

        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: GetSubClassesRes) {
            buffer.writeArray(value.classes.toTypedArray()) {
                GetClassRes.write(ctx, buffer, it)
            }
        }
    }
}