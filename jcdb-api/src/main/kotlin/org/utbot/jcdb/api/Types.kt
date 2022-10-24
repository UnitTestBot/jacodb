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

    suspend fun typeParameters(): List<JcTypeVariableDeclaration>
    suspend fun typeArguments(): List<JcRefType>

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

    suspend fun outerType(): JcClassType?

    suspend fun declaredMethods(): List<JcTypedMethod>
    suspend fun methods(): List<JcTypedMethod>

    suspend fun declaredFields(): List<JcTypedField>
    suspend fun fields(): List<JcTypedField>

    suspend fun typeParameters(): List<JcTypeVariableDeclaration>
    suspend fun typeArguments(): List<JcRefType>

    suspend fun superType(): JcClassType?
    suspend fun interfaces(): List<JcClassType>

    suspend fun innerTypes(): List<JcClassType>
}

interface JcTypeVariable : JcRefType {
    val symbol: String

    val bounds: List<JcRefType>
}

interface JcBoundedWildcard : JcRefType {
    val upperBounds: List<JcRefType>
    val lowerBounds: List<JcRefType>
}

interface JcUnboundWildcard : JcRefType

interface JcTypeVariableDeclaration {
    val symbol: String
    val bounds: List<JcRefType>
    val owner: JcAccessible
}