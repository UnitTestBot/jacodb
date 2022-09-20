package org.utbot.jcdb.remote.rd

import com.jetbrains.rd.framework.AbstractBuffer
import com.jetbrains.rd.framework.IMarshaller
import com.jetbrains.rd.framework.SerializationCtx
import com.jetbrains.rd.framework.Serializers
import com.jetbrains.rd.framework.readArray
import com.jetbrains.rd.framework.writeArray
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.utbot.jcdb.api.LocationType
import org.utbot.jcdb.api.PredefinedPrimitives
import java.io.Serializable
import kotlin.reflect.KClass

val serializers = Serializers()

class GetClasspathReq(val locations: List<String>) {

    companion object : IMarshaller<GetClasspathReq> {

        override val _type: KClass<GetClasspathReq> = GetClasspathReq::class

        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): GetClasspathReq {
            return GetClasspathReq(buffer.readArray {
                buffer.readString()
            }.toList())
        }

        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: GetClasspathReq) {
            buffer.writeArray(value.locations.toTypedArray()) {
                buffer.writeString(it)
            }
        }
    }
}

class GetClasspathRes(val key: String, val locations: List<String>, val scopes: List<LocationType>) {

    companion object : IMarshaller<GetClasspathRes> {

        override val _type: KClass<GetClasspathRes> = GetClasspathRes::class

        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): GetClasspathRes {
            return GetClasspathRes(
                buffer.readString(),
                buffer.readArray {
                    buffer.readString()
                }.toList(),
                buffer.readArray {
                    LocationType.values()[buffer.readInt()]
                }.toList(),
            )
        }

        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: GetClasspathRes) {
            buffer.writeString(value.key)
            buffer.writeArray(value.locations.toTypedArray()) {
                buffer.writeString(it)
            }
            buffer.writeArray(value.scopes.toTypedArray()) {
                buffer.writeInt(it.ordinal)
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

class CallIndexReq(cpKey: String, val indexKey: String, val term: Serializable) :
    ClasspathBasedReq(cpKey) {

    companion object : IMarshaller<CallIndexReq> {

        override val _type: KClass<CallIndexReq> = CallIndexReq::class

        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CallIndexReq {
            return CallIndexReq(
                buffer.readString(),
                buffer.readString(),
                Cbor.decodeFromByteArray(buffer.readByteArray())
            )
        }

        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CallIndexReq) {
            buffer.writeString(value.cpKey)
            buffer.writeString(value.indexKey)
            buffer.writeByteArray(Cbor.encodeToByteArray(value.term))
        }
    }
}

class CallIndexRes(val type: String, val result: List<*>) {

    companion object : IMarshaller<CallIndexRes> {

        override val _type: KClass<CallIndexRes> = CallIndexRes::class

        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CallIndexRes {
            val type = buffer.readString()
            val array = when (type) {
                PredefinedPrimitives.boolean -> buffer.readBooleanArray().toList()
                PredefinedPrimitives.byte -> buffer.readByteArray().toList()
                PredefinedPrimitives.short -> buffer.readShortArray().toList()
                PredefinedPrimitives.int -> buffer.readIntArray().toList()
                PredefinedPrimitives.long -> buffer.readLongArray().toList()
                PredefinedPrimitives.float -> buffer.readFloatArray().toList()
                PredefinedPrimitives.double -> buffer.readDoubleArray().toList()
                "string" -> buffer.readArray { buffer.readString() }.toList()
                "unknown" -> buffer.readBooleanArray().toList() // will be empty list
                else -> throw UnsupportedOperationException("$type is unsupported")
            }
            return CallIndexRes(type, array)
        }

        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CallIndexRes) {
            val type = value.type
            buffer.writeString(type)
            when (type) {
                PredefinedPrimitives.boolean -> buffer.writeBooleanArray((value.result as List<Boolean>).toBooleanArray())
                PredefinedPrimitives.byte -> buffer.writeByteArray((value.result as List<Byte>).toByteArray())
                PredefinedPrimitives.char -> buffer.writeCharArray((value.result as List<Char>).toCharArray())
                PredefinedPrimitives.short -> buffer.writeShortArray((value.result as List<Short>).toShortArray())
                PredefinedPrimitives.int -> buffer.writeIntArray((value.result as List<Int>).toIntArray())
                PredefinedPrimitives.long -> buffer.writeLongArray((value.result as List<Long>).toLongArray())
                PredefinedPrimitives.float -> buffer.writeFloatArray((value.result as List<Float>).toFloatArray())
                PredefinedPrimitives.double -> buffer.writeDoubleArray((value.result as List<Double>).toDoubleArray())
                "string" -> buffer.writeArray((value.result as List<String>).toTypedArray()) {
                    buffer.writeString(it)
                }
                "unknown" -> buffer.writeBooleanArray(BooleanArray(0))
                else -> throw UnsupportedOperationException("$type is unsupported")
            }
        }
    }
}