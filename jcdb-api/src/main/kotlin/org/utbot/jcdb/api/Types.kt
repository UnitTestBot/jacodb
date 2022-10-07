package org.utbot.jcdb.api

import org.objectweb.asm.tree.LocalVariableNode

interface JcTypedField {
    val name: String

    val field: JcField
    suspend fun fieldType(): JcType
    val enclosingType: JcRefType
}

interface JcTypedMethod {
    val name: String
    suspend fun returnType(): JcType

    suspend fun originalParameterization(): List<JcTypeVariableDeclaration>
    suspend fun parameterization(): Map<String, JcRefType>

    suspend fun parameters(): List<JcTypedMethodParameter>
    suspend fun exceptions(): List<JcClassOrInterface>
    val method: JcMethod

    val enclosingType: JcRefType

    suspend fun typeOf(inst: LocalVariableNode): JcType

}

interface JcTypedMethodParameter {
    suspend fun type(): JcType
    val name: String?
    val enclosingMethod: JcTypedMethod
    val nullable: Boolean
}

interface JcType {
    val classpath: JcClasspath
    val typeName: String

    val nullable: Boolean
}

interface JcPrimitiveType : JcType {
    override val nullable: Boolean
        get() = false
}

interface JcRefType : JcType {
    fun notNullable(): JcRefType
}

interface JcArrayType : JcRefType {
    val elementType: JcType
}

interface JcClassType : JcRefType {

    val jcClass: JcClassOrInterface

    suspend fun methods(): List<JcTypedMethod>
    suspend fun fields(): List<JcTypedField>

    suspend fun originalParametrization(): List<JcTypeVariableDeclaration>
    suspend fun parametrization(): Map<String, JcRefType>

    suspend fun superType(): JcRefType?
    suspend fun interfaces(): List<JcRefType>

    suspend fun outerType(): JcRefType?
    suspend fun outerMethod(): JcTypedMethod?

    suspend fun innerTypes(): List<JcRefType>
}

interface JcTypeVariable : JcRefType {
    val symbol: String

    val bounds: List<JcRefType>
}

interface JcLowerBoundWildcard : JcRefType {
    val boundType: JcRefType
}
interface JcUnboundWildcard : JcRefType

interface JcTypeVariableDeclaration {
    val symbol: String
    val bounds: List<JcRefType>
}