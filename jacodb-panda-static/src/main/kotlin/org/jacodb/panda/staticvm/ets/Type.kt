/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jacodb.panda.staticvm.ets

/**
 * Type descriptor.
 *
 * ```
 * TypeDescriptor -> PrimitiveType | ArrayType | RefType
 * PrimitiveType  -> 'Z' | 'B' | 'H' | 'S' | 'C' | 'I' | 'U' | 'J' | 'Q' | 'F' | 'D' | 'A'
 * ArrayType      -> '[' TypeDescriptor
 * RefType        -> 'L' ClassName ';'
 * ```
 *
 * `PrimitiveType` is a one letter encoding for primitive type
 *
 * | Type  | Encoding |
 * | ----  | :--: |
 * | `u1`  | `Z` |
 * | `i8`  | `B` |
 * | `u8`  | `H` |
 * | `i16` | `S` |
 * | `u16` | `C` |
 * | `i32` | `I` |
 * | `u32` | `U` |
 * | `f32` | `F` |
 * | `f64` | `D` |
 * | `i64` | `J` |
 * | `u64` | `Q` |
 * | `any` | `A` |
 *
 * `ClassName` is a qualified name of a class with `.` replaced with `/`.
 */
data class TypeDesc(val typeDesc: String) {
    override fun toString(): String = typeDesc

    // override fun equals(other: Any?): Boolean {
    //     if (this === other) return true
    //     if (other !is TypeDesc) return false
    //     return typeDesc == other.typeDesc
    // }
    //
    // override fun hashCode(): Int {
    //     return typeDesc.hashCode()
    // }

    fun getKind(): TypeKind {
        TODO()
    }

    fun isValueType(): Boolean {
        TODO()
    }

    fun getArrayElementType(): TypeDesc {
        TODO()
    }

    companion object {
        val U1: TypeDesc = TypeDesc("Z")
        val I8: TypeDesc = TypeDesc("B")
        val U8: TypeDesc = TypeDesc("H")
        val I16: TypeDesc = TypeDesc("S")
        val U16: TypeDesc = TypeDesc("C")
        val I32: TypeDesc = TypeDesc("I")
        val U32: TypeDesc = TypeDesc("U")
        val F32: TypeDesc = TypeDesc("F")
        val F64: TypeDesc = TypeDesc("D")
        val I64: TypeDesc = TypeDesc("J")
        val U64: TypeDesc = TypeDesc("Q")
        val ANY: TypeDesc = TypeDesc("A")
        fun array(elemType: TypeDesc): TypeDesc = TypeDesc("[$elemType")
        fun ref(className: String): TypeDesc = TypeDesc("L${className.replace('.', '/')};")
    }
}

/**
 * Type kind.
 */
enum class TypeKind(val value: Byte) {
    NONE(0x0),
    VOID(0x1),

    CHAR(0x2),
    BOOLEAN(0x3),
    BYTE(0x4),
    SHORT(0x5),
    INT(0x6),
    LONG(0x7),
    FLOAT(0x8),
    DOUBLE(0x9),

    CLASS(0xA),
    STRING(0xB),
    INTERFACE(0xC),
    ARRAY(0xD),
    TUPLE(0xE),
    LAMBDA(0xF),
    METHOD(0x10),

    UNION(0x11),
    UNDEFINED(0x12),
    NULL(0x13),

    ENUM(0x14),
}

// 5 lower bits for type kind
const val TYPE_KIND_MASK = (1 shl 6) - 1

/**
 * Value type descriptor.
 */
object ValueTypeDesc {
    val BOOLEAN: TypeDesc = TypeDesc.U1
    val BYTE: TypeDesc = TypeDesc.I8
    val SHORT: TypeDesc = TypeDesc.I16
    val CHAR: TypeDesc = TypeDesc.U16
    val INT: TypeDesc = TypeDesc.I32
    val LONG: TypeDesc = TypeDesc.I64
    val FLOAT: TypeDesc = TypeDesc.F32
    val DOUBLE: TypeDesc = TypeDesc.F64
}

val OBJECT_TYPE_DESC = TypeDesc.ref("std.core.Object")

val ObjectType = ClassType(OBJECT_TYPE_DESC, Attributes(0))

/**
 * Attribute flags.
 */
data class Attributes(val value: Int) {

    fun isStatic(): Boolean = (value and STATIC) != 0
    fun isInherited(): Boolean = (value and INHERITED) != 0
    fun isReadOnly(): Boolean = (value and READONLY) != 0
    fun isFinal(): Boolean = (value and FINAL) != 0
    fun isAbstract(): Boolean = (value and ABSTRACT) != 0
    fun isConstructor(): Boolean = (value and CONSTRUCTOR) != 0
    fun isRest(): Boolean = (value and REST) != 0
    fun isOptional(): Boolean = (value and OPTIONAL) != 0
    fun isThrowing(): Boolean = (value and THROWING) != 0
    fun isNative(): Boolean = (value and NATIVE) != 0
    fun isAsync(): Boolean = (value and ASYNC) != 0
    fun isNeverResult(): Boolean = (value and NEVERRESULT) != 0
    fun isGetter(): Boolean = (value and GETTER) != 0
    fun isSetter(): Boolean = (value and SETTER) != 0

    companion object {
        const val STATIC: Int = 1 shl 0
        const val INHERITED: Int = 1 shl 1
        const val READONLY: Int = 1 shl 2
        const val FINAL: Int = 1 shl 3
        const val ABSTRACT: Int = 1 shl 4
        const val CONSTRUCTOR: Int = 1 shl 5
        const val REST: Int = 1 shl 6
        const val OPTIONAL: Int = 1 shl 7
        const val THROWING: Int = 1 shl 8
        const val NATIVE: Int = 1 shl 9
        const val ASYNC: Int = 1 shl 10
        const val NEVERRESULT: Int = 1 shl 11
        const val GETTER: Int = 1 shl 12
        const val SETTER: Int = 1 shl 13
    }
}

/**
 * Access modifier.
 */
enum class AccessModifier(val value: Byte) {
    PUBLIC(0),
    PRIVATE(1),
    PROTECTED(2),
}

abstract class Type {
    abstract val td: TypeDesc

    abstract fun isPrimitive(): Boolean
    abstract fun isReference(): Boolean
    abstract fun hasName(): Boolean
    abstract fun getName(): String
    abstract fun getLiteral(): String

    /**
     * Checks if this type is a subtype of another type.
     *
     * `T.subTypeOf(U)` means `T extends U`
     */
    open fun subTypeOf(other: Type): Boolean {
        if (this == other) return true
        if (other == ObjectType) {
            val isNullish = this is UndefinedType || this is NullType
            return this.isReference() && !isNullish
        }
        return false
    }

    /**
     * Checks if this type is assignable from another type.
     *
     * `T.assignableFrom(U)` means `T <- U`
     */
    open fun assignableFrom(other: Type): Boolean {
        if (other.subTypeOf(this)) return true
        if (this.isNumericType() && other.isNumericType()) return true
        return false
    }

    private fun isNumericType(): Boolean =
        this is ByteType
            || this is ShortType
            || this is IntType
            || this is LongType
            || this is FloatType
            || this is DoubleType

    override fun toString(): String = if (hasName()) getName() else getLiteral()

    companion object {
        fun resolve(td: TypeDesc): Type? {
            return when (td.getKind()) {
                TypeKind.NONE -> null
                TypeKind.VOID -> VoidType.REF
                TypeKind.CHAR -> TODO()
                TypeKind.BOOLEAN -> TODO()
                TypeKind.BYTE -> TODO()
                TypeKind.SHORT -> TODO()
                TypeKind.INT -> TODO()
                TypeKind.LONG -> TODO()
                TypeKind.FLOAT -> TODO()
                TypeKind.DOUBLE -> TODO()
                TypeKind.CLASS -> TODO()
                TypeKind.STRING -> TODO()
                TypeKind.INTERFACE -> TODO()
                TypeKind.ARRAY -> TODO()
                TypeKind.TUPLE -> TODO()
                TypeKind.LAMBDA -> TODO()
                TypeKind.METHOD -> TODO()
                TypeKind.UNION -> TODO()
                TypeKind.UNDEFINED -> TODO()
                TypeKind.NULL -> TODO()
                TypeKind.ENUM -> TODO()
                // else -> null
            }
        }
    }
}

class NullType private constructor(
    override val td: TypeDesc,
) : Type() {

    override fun isPrimitive(): Boolean = true
    override fun isReference(): Boolean = true
    override fun hasName(): Boolean = true
    override fun getName(): String = "null"
    override fun getLiteral(): String = "null"

    override fun equals(other: Any?): Boolean {
        return other is NullType
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }

    companion object {
        val REF: NullType = NullType(TypeDesc("null"))
    }
}

class UndefinedType private constructor(
    override val td: TypeDesc,
) : Type() {

    override fun isPrimitive(): Boolean = true
    override fun isReference(): Boolean = true
    override fun hasName(): Boolean = true
    override fun getName(): String = "undefined"
    override fun getLiteral(): String = "undefined"

    override fun equals(other: Any?): Boolean {
        return other is UndefinedType
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }

    companion object {
        val REF: UndefinedType = UndefinedType(TypeDesc("undefined"))
    }
}

class VoidType private constructor(
    override val td: TypeDesc,
) : Type() {

    override fun isPrimitive(): Boolean = true
    override fun isReference(): Boolean = true
    override fun hasName(): Boolean = true
    override fun getName(): String = "void"
    override fun getLiteral(): String = "void"

    override fun equals(other: Any?): Boolean {
        return other is VoidType
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }

    companion object {
        val REF: VoidType = VoidType(TypeDesc("void"))
    }
}

class CharType private constructor(
    override val td: TypeDesc,
    private val isValue: Boolean,
) : Type() {

    override fun isPrimitive(): Boolean = true
    override fun isReference(): Boolean = !isValue
    override fun hasName(): Boolean = !isValue
    override fun getName(): String = if (isValue) "" else td.toString()
    override fun getLiteral(): String = if (isValue) "char" else "Char"

    override fun equals(other: Any?): Boolean {
        return other is CharType && this.isValue != other.isReference()
    }

    companion object {
        val VAL: CharType = CharType(ValueTypeDesc.CHAR, true)
        val REF: CharType = CharType(TypeDesc.ref("std.core.Char"), false)
    }
}

class BooleanType(
    override val td: TypeDesc,
    private val isValue: Boolean,
) : Type() {

    override fun isPrimitive(): Boolean = true
    override fun isReference(): Boolean = !isValue
    override fun hasName(): Boolean = !isValue
    override fun getName(): String = if (isValue) "" else td.toString()
    override fun getLiteral(): String = if (isValue) "boolean" else "Boolean"

    override fun equals(other: Any?): Boolean {
        return other is BooleanType && this.isValue != other.isReference()
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }

    companion object {
        val VAL: BooleanType = BooleanType(ValueTypeDesc.BOOLEAN, true)
        val REF: BooleanType = BooleanType(TypeDesc.ref("std.core.Boolean"), false)
    }
}

class ByteType(
    override val td: TypeDesc,
    private val isValue: Boolean,
) : Type() {

    override fun isPrimitive(): Boolean = true
    override fun isReference(): Boolean = !isValue
    override fun hasName(): Boolean = !isValue
    override fun getName(): String = if (isValue) "" else td.toString()
    override fun getLiteral(): String = if (isValue) "byte" else "Byte"

    override fun equals(other: Any?): Boolean {
        return other is ByteType && this.isValue != other.isReference()
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }

    companion object {
        val VAL: ByteType = ByteType(ValueTypeDesc.BYTE, true)
        val REF: ByteType = ByteType(TypeDesc.ref("std.core.Byte"), false)
    }
}

class ShortType(
    override val td: TypeDesc,
    private val isValue: Boolean,
) : Type() {

    override fun isPrimitive(): Boolean = true
    override fun isReference(): Boolean = !isValue
    override fun hasName(): Boolean = !isValue
    override fun getName(): String = if (isValue) "" else td.toString()
    override fun getLiteral(): String = if (isValue) "short" else "Short"

    override fun equals(other: Any?): Boolean {
        return other is ShortType && this.isValue != other.isReference()
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }

    companion object {
        val VAL: ShortType = ShortType(ValueTypeDesc.SHORT, true)
        val REF: ShortType = ShortType(TypeDesc.ref("std.core.Short"), false)
    }
}

class IntType(
    override val td: TypeDesc,
    private val isValue: Boolean,
) : Type() {

    override fun isPrimitive(): Boolean = true
    override fun isReference(): Boolean = !isValue
    override fun hasName(): Boolean = !isValue
    override fun getName(): String = if (isValue) "" else td.toString()
    override fun getLiteral(): String = if (isValue) "int" else "Int"

    companion object {
        val VAL: IntType = IntType(ValueTypeDesc.INT, true)
        val REF: IntType = IntType(TypeDesc.ref("std.core.Int"), false)
    }
}

class LongType(
    override val td: TypeDesc,
    private val isValue: Boolean,
) : Type() {

    override fun isPrimitive(): Boolean = true
    override fun isReference(): Boolean = !isValue
    override fun hasName(): Boolean = !isValue
    override fun getName(): String = if (isValue) "" else td.toString()
    override fun getLiteral(): String = if (isValue) "long" else "Long"

    override fun equals(other: Any?): Boolean {
        return other is LongType && this.isValue != other.isReference()
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }

    companion object {
        val VAL: LongType = LongType(ValueTypeDesc.LONG, true)
        val REF: LongType = LongType(TypeDesc.ref("std.core.Long"), false)
    }
}

class FloatType(
    override val td: TypeDesc,
    private val isValue: Boolean,
) : Type() {

    override fun isPrimitive(): Boolean = true
    override fun isReference(): Boolean = !isValue
    override fun hasName(): Boolean = !isValue
    override fun getName(): String = if (isValue) "" else td.toString()
    override fun getLiteral(): String = if (isValue) "float" else "Float"

    override fun equals(other: Any?): Boolean {
        return other is FloatType && this.isValue != other.isReference()
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }

    companion object {
        val VAL: FloatType = FloatType(ValueTypeDesc.FLOAT, true)
        val REF: FloatType = FloatType(TypeDesc.ref("std.core.Float"), false)
    }
}

class DoubleType(
    override val td: TypeDesc,
    private val isValue: Boolean,
) : Type() {

    override fun isPrimitive(): Boolean = true
    override fun isReference(): Boolean = !isValue
    override fun hasName(): Boolean = !isValue
    override fun getName(): String = if (isValue) "" else td.toString()
    override fun getLiteral(): String = if (isValue) "double" else "Double"

    override fun equals(other: Any?): Boolean {
        return other is DoubleType && this.isValue != other.isReference()
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }

    companion object {
        val VAL: DoubleType = DoubleType(ValueTypeDesc.DOUBLE, true)
        val REF: DoubleType = DoubleType(TypeDesc.ref("std.core.Double"), false)
    }
}

class ClassType(
    override val td: TypeDesc,
    val attrs: Attributes,
) : Type() {

    override fun isPrimitive(): Boolean = false
    override fun isReference(): Boolean = true
    override fun hasName(): Boolean = true
    override fun getName(): String = td.toString()
    override fun getLiteral(): String = "class {...}"  // TODO

    fun getBaseType(): ClassType {
        TODO()
    }

    fun isFinal(): Boolean = attrs.isFinal()

    override fun subTypeOf(other: Type): Boolean {
        if (super.subTypeOf(other)) return true
        if (other is ClassType) {
            var bt = this
            while (bt != bt.getBaseType()) {
                if (bt == other) return true
                bt = bt.getBaseType()
            }
            return false
        }
        // if (other is InterfaceType) {
        //     TODO()
        // }
        return false
    }

    override fun equals(other: Any?): Boolean {
        return other is ClassType && other.td == this.td
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }
}

// TODO: InterfaceType

class ArrayType(
    override val td: TypeDesc,
    val elemTD: TypeDesc,
) : Type() {

    override fun isPrimitive(): Boolean = false
    override fun isReference(): Boolean = true
    override fun hasName(): Boolean = false
    override fun getName(): String = ""
    override fun getLiteral(): String = "${getElementType()}[]"

    fun getElementType(): Type {
        // TODO: return Type.resolve(elemTD)!!
        TODO()
    }

    override fun assignableFrom(other: Type): Boolean {
        if (super.assignableFrom(other)) return true
        if (other is ArrayType) {
            return this.getElementType().subTypeOf(other.getElementType())
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (other is ArrayType) {
            return this.getElementType() == other.getElementType()
        }
        return false
    }

    override fun hashCode(): Int {
        return elemTD.hashCode()
    }

    companion object {
        private fun arrayWithElements(elemTD: TypeDesc): ArrayType = ArrayType(TypeDesc.array(elemTD), elemTD)

        val BOOLEAN_VAL: ArrayType = arrayWithElements(ValueTypeDesc.BOOLEAN)
        val BOOLEAN_REF: ArrayType = arrayWithElements(TypeDesc.ref("std.core.Boolean"))
        val CHAR_VAL: ArrayType = arrayWithElements(ValueTypeDesc.CHAR)
        val CHAR_REF: ArrayType = arrayWithElements(TypeDesc.ref("std.core.Char"))
        val BYTE_VAL: ArrayType = arrayWithElements(ValueTypeDesc.BYTE)
        val BYTE_REF: ArrayType = arrayWithElements(TypeDesc.ref("std.core.Byte"))
        val SHORT_VAL: ArrayType = arrayWithElements(ValueTypeDesc.SHORT)
        val SHORT_REF: ArrayType = arrayWithElements(TypeDesc.ref("std.core.Short"))
        val INT_VAL: ArrayType = arrayWithElements(ValueTypeDesc.INT)
        val INT_REF: ArrayType = arrayWithElements(TypeDesc.ref("std.core.Int"))
        val LONG_VAL: ArrayType = arrayWithElements(ValueTypeDesc.LONG)
        val LONG_REF: ArrayType = arrayWithElements(TypeDesc.ref("std.core.Long"))
        val FLOAT_VAL: ArrayType = arrayWithElements(ValueTypeDesc.FLOAT)
        val FLOAT_REF: ArrayType = arrayWithElements(TypeDesc.ref("std.core.Float"))
        val DOUBLE_VAL: ArrayType = arrayWithElements(ValueTypeDesc.DOUBLE)
        val DOUBLE_REF: ArrayType = arrayWithElements(TypeDesc.ref("std.core.Double"))

        internal fun getInstance(td: TypeDesc, elemTD: TypeDesc): ArrayType {
            return when (elemTD.getKind()) {
                TypeKind.BOOLEAN -> if (elemTD.isValueType()) BOOLEAN_VAL else BOOLEAN_REF
                TypeKind.CHAR -> if (elemTD.isValueType()) CHAR_VAL else CHAR_REF
                TypeKind.BYTE -> if (elemTD.isValueType()) BYTE_VAL else BYTE_REF
                TypeKind.SHORT -> if (elemTD.isValueType()) SHORT_VAL else SHORT_REF
                TypeKind.INT -> if (elemTD.isValueType()) INT_VAL else INT_REF
                TypeKind.LONG -> if (elemTD.isValueType()) LONG_VAL else LONG_REF
                TypeKind.FLOAT -> if (elemTD.isValueType()) FLOAT_VAL else FLOAT_REF
                TypeKind.DOUBLE -> if (elemTD.isValueType()) DOUBLE_VAL else DOUBLE_REF
                TypeKind.CLASS -> ArrayType(td, elemTD)
                TypeKind.INTERFACE -> ArrayType(td, elemTD)
                TypeKind.STRING -> ArrayType(td, elemTD)
                TypeKind.ARRAY -> ArrayType(td, elemTD)
                TypeKind.TUPLE -> ArrayType(td, elemTD)
                TypeKind.LAMBDA -> ArrayType(td, elemTD)
                TypeKind.METHOD -> ArrayType(td, elemTD)
                TypeKind.UNION -> ArrayType(td, elemTD)
                else -> error("Cannot create ArrayType with element type $elemTD")
            }
        }

        internal fun getInstance(td: TypeDesc): ArrayType {
            return getInstance(td, td.getArrayElementType())
        }
    }
}

// TODO: TupleType

abstract class FunctionType(
    override val td: TypeDesc,
    val attrs: Attributes,
) : Type() {

    override fun isPrimitive(): Boolean = false
    override fun isReference(): Boolean = true
    override fun hasName(): Boolean = false
    override fun getName(): String = ""

    fun getResultType(): Type {
        // TODO: return Type.resolve(td.getResultType())
        TODO()
    }

    fun isThrowing(): Boolean = attrs.isThrowing()
    fun isNative(): Boolean = attrs.isNative()
    fun isAsync(): Boolean = attrs.isAsync()
    fun isNeverResult(): Boolean = attrs.isNeverResult()

    override fun equals(other: Any?): Boolean {
        return other is FunctionType && other.td == this.td
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }
}

class LambdaType(
    td: TypeDesc,
    attrs: Attributes,
) : FunctionType(td, attrs) {

    override fun getLiteral(): String = buildString {
        append("(")
        append("...TODO...")
        // TODO:
        // val paramsNum = getParametersNum()
        // for (i in 0 until paramsNum) {
        //     if (i > 0) {
        //         append(", ")
        //     }
        //     append(getParameter(i))
        // }
        append("): ")
        append(getResultType())
    }

    fun getReceiverType(): Type {
        // TODO: return Type.resolve(td.getReceiverType())
        TODO()
    }

    fun getParametersNum(): Int {
        TODO()
    }

    fun getParameter(index: Int): Parameter {
        TODO()
    }

    override fun assignableFrom(other: Type): Boolean {
        if (super.assignableFrom(other)) return true
        if (other !is LambdaType) return false

        if (this.getParametersNum() != other.getParametersNum()) return false

        // Parameter types are using contravariance
        for (i in 0 until this.getParametersNum()) {
            // if (this.getParameter(i) != other.getParameter(i)) return false
            val lt = this.getParameter(i).type
            val rt = other.getParameter(i).type
            if (!lt.subTypeOf(rt)) return false
        }

        // Return types are using covariance
        return other.getResultType().subTypeOf(this.getResultType())
    }
}

class MethodType(
    td: TypeDesc,
    attrs: Attributes,
) : FunctionType(td, attrs) {

    override fun getLiteral(): String = buildString {
        append("(")
        append("...TODO...")
        append("): ")
        append(getResultType())
    }

    fun getReceiverType(): Type {
        // TODO: return Type.resolve(td.getReceiverType())
        TODO()
    }

    override fun assignableFrom(other: Type): Boolean {
        return false
    }
}

class StringType(
    override val td: TypeDesc,
) : Type() {

    override fun isPrimitive(): Boolean = true
    override fun isReference(): Boolean = true
    override fun hasName(): Boolean = false
    override fun getName(): String = ""
    override fun getLiteral(): String = "string" // TODO

    override fun equals(other: Any?): Boolean {
        return other is StringType
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }
}

class EnumType(
    override val td: TypeDesc,
) : Type() {
    override fun isPrimitive(): Boolean = false
    override fun isReference(): Boolean = false
    override fun hasName(): Boolean = true
    override fun getName(): String = td.toString()
    override fun getLiteral(): String = "enum {...}" // TODO

    override fun assignableFrom(other: Type): Boolean {
        if (super.assignableFrom(other)) return true
        if (other is EnumType) {
            return this.getName() == other.getName()
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        // TODO
        return false
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }
}

class UnionType(
    override val td: TypeDesc,
) : Type() {
    override fun isPrimitive(): Boolean = false
    override fun isReference(): Boolean = true
    override fun hasName(): Boolean = false
    override fun getName(): String = ""
    override fun getLiteral(): String = "(... | ...)" // TODO

    override fun equals(other: Any?): Boolean {
        // TODO
        return false
    }

    override fun hashCode(): Int {
        return td.hashCode()
    }
}
