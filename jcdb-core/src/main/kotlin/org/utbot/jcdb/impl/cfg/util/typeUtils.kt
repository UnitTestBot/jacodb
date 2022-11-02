package org.utbot.jcdb.impl.cfg.util

import org.utbot.jcdb.api.*
import org.utbot.jcdb.impl.types.TypeNameImpl

internal val NULL = "null".typeName()
internal const val STRING_CLASS = "java.lang.String"
internal const val THROWABLE_CLASS = "java.lang.Throwable"
internal const val CLASS_CLASS = "java.lang.Class"
internal const val METHOD_HANDLE_CLASS = "java.lang.invoke.MethodHandle"

// TODO: decide what to do with this
data class MethodTypeNameImpl(
    val argTypes: List<TypeName>,
    val returnType: TypeName
) : TypeName {
    override val typeName: String
        get() = "(${argTypes.joinToString(", ")})$returnType"

}

internal val TypeName.jvmTypeName get() = typeName.jvmName()

internal val TypeName.isPrimitive get() = PredefinedPrimitives.matches(typeName)

internal val TypeName.isDWord
    get() = when (typeName) {
        PredefinedPrimitives.long -> true
        PredefinedPrimitives.double -> true
        else -> false
    }

internal fun String.typeName(): TypeName = TypeNameImpl(jcdbName())
internal fun TypeName.asArray(dimensions: Int = 1) = "$typeName${"[]".repeat(dimensions)}".typeName()
internal fun TypeName.elementType() = elementTypeOrNull() ?: error("Attempting to get element type of non-array type $this")

internal fun TypeName.elementTypeOrNull() = when {
    typeName.endsWith("[]") -> typeName.removeSuffix("[]").typeName()
    else -> null
}
internal fun TypeName.baseElementType(): TypeName {
    var current: TypeName? = this
    var next: TypeName? = current
    do {
        current = next
        next = current!!.elementTypeOrNull()
    } while (next != null)
    return current!!
}


internal fun JcRawNull() = JcRawNullConstant(NULL)
internal fun JcRawBool(value: Boolean) = JcRawBool(value, PredefinedPrimitives.boolean.typeName())
internal fun JcRawByte(value: Byte) = JcRawByte(value, PredefinedPrimitives.byte.typeName())
internal fun JcRawShort(value: Short) = JcRawShort(value, PredefinedPrimitives.short.typeName())
internal fun JcRawChar(value: Char) = JcRawChar(value, PredefinedPrimitives.char.typeName())
internal fun JcRawInt(value: Int) = JcRawInt(value, PredefinedPrimitives.int.typeName())
internal fun JcRawLong(value: Long) = JcRawLong(value, PredefinedPrimitives.long.typeName())
internal fun JcRawFloat(value: Float) = JcRawFloat(value, PredefinedPrimitives.float.typeName())
internal fun JcRawDouble(value: Double) = JcRawDouble(value, PredefinedPrimitives.double.typeName())

internal fun JcRawZero(typeName: TypeName) = when (typeName.typeName) {
    PredefinedPrimitives.boolean -> JcRawBool(false)
    PredefinedPrimitives.byte -> JcRawByte(0)
    PredefinedPrimitives.char -> JcRawChar(0.toChar())
    PredefinedPrimitives.short -> JcRawShort(0)
    PredefinedPrimitives.int -> JcRawInt(0)
    PredefinedPrimitives.long -> JcRawLong(0)
    PredefinedPrimitives.float -> JcRawFloat(0.0f)
    PredefinedPrimitives.double -> JcRawDouble(0.0)
    else -> error("Unknown primitive type: $typeName")
}

internal fun JcRawNumber(number: Number) = when (number) {
    is Int -> JcRawInt(number)
    is Float -> JcRawFloat(number)
    is Long -> JcRawLong(number)
    is Double -> JcRawDouble(number)
    else -> error("Unknown number: $number")
}

internal fun JcRawString(value: String) = JcRawStringConstant(value, STRING_CLASS.typeName())
