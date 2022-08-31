package org.utbot.jcdb.design

interface JcTypeField {
    val name: String
    val type: JcType
    val declaringClass: ClassId
    val declaringType: JcType
}

interface JcTypeMethod {
    val name: String
    val returnType: JcType
    val parameters: List<JcTypeMethodParameter>
}

interface JcTypeMethodParameter {
    val annotations: List<JcAnnotation>
    val type: JcType
}

// dynamic data
open class JcType

open class JcPrimitiveType : JcType()

abstract class JcRefType : JcType() {
    val jcClass: JcClassOrInterface get() = TODO()
}

open class JcArrayType() : JcRefType() {
    val elementType: JcType get() = TODO()
}

open class JcParametrizedType : JcRefType() { //List<String> -> List
    // T1 -> S1, T2 -> S2
    val parameterTypes: List<JcType> get() = TODO()
}

open class JcClassType : JcRefType()

open class JcTypeVariable() : JcRefType()


interface Classpath {
    fun findOrNull(className: String) : ClassId

    fun typeOf(): JcType
}
interface ClassId {}