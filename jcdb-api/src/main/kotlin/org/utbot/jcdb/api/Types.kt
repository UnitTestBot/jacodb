package org.utbot.jcdb.api

import org.objectweb.asm.tree.LocalVariableNode

interface JcTypedField {
    val name: String

    val field: JcField
    val fieldType: JcType
    val ownerType: JcRefType
}

interface JcTypedMethod {
    val name: String
    suspend fun returnType(): JcType

    suspend fun parameterization(): List<JcTypeVariableDeclaration>
    suspend fun parameters(): List<JcTypedMethodParameter>
    suspend fun exceptions(): List<JcClassOrInterface>
    val method: JcMethod

    val ownerType: JcRefType

    suspend fun typeOf(inst: LocalVariableNode): JcType

}

interface JcTypedMethodParameter {
    suspend fun type(): JcType
    val name: String?
    val ownerMethod: JcTypedMethod
    val nullable: Boolean
}

interface JcType {
    val classpath: JcClasspath
    val typeName: String

    val nullable: Boolean
}

// boolean, int, float, double, void (?) etc
interface JcPrimitiveType : JcType {
    override val nullable: Boolean
        get() = false
}

interface JcRefType : JcType {
    val jcClass: JcClassOrInterface

    val methods: List<JcTypedMethod>
    val fields: List<JcTypedField>

    fun notNullable(): JcRefType
}

// -----------
// Array<T: Any> -> JcArrayType(JcTypeVariable())
// -----------
// Array<Any> -> JcArrayType(JcClassType())
// -----------
// Array<List<T>> -> JcArrayType(JcParametrizedType(JcClassType(), JcTypeVariable()))
interface JcArrayType : JcRefType {
    val elementType: JcType
}

// -----------
// class A<T> -> JcParametrizedType(JcTypeVariableDeclaration('T', emptyList()))
interface JcParametrizedType : JcRefType {
    val originParametrization: List<JcTypeVariableDeclaration>
    val parametrization: List<JcRefType>
}

// java.lang.String -> JcClassType()
interface JcClassType : JcRefType {
    suspend fun superType(): JcRefType?
    suspend fun interfaces(): List<JcRefType>

    suspend fun outerType(): JcRefType?
    suspend fun outerMethod(): JcTypedMethod?

    suspend fun innerTypes(): List<JcRefType>
}

// -----------
// class A<T> -> JcParametrizedType(JcTypeVariable())
// -----------
// class A : B<T> -> type = JcClassType(), type.superType = JcParametrizedType(JcTypeVariable())
// -----------
// class A<T> {
//      val field: T
// }
// A<T> -> type = JcParametrizedType(JcTypeVariable()), type.fields[0].type = JcTypeVariable("T")
// -----------
interface JcTypeVariable : JcRefType {
    val typeSymbol: String
}


interface JcBoundWildcard : JcRefType {
    val boundType: JcRefType
}

interface JcUpperBoundWildcard : JcBoundWildcard
interface JcLowerBoundWildcard : JcBoundWildcard
interface JcUnboundWildcard : JcRefType


interface JcTypeVariableDeclaration {
    val symbol: String
    val bounds: List<JcRefType>
}