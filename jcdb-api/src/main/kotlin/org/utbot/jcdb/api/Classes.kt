package org.utbot.jcdb.api

import org.objectweb.asm.tree.MethodNode

interface JcClassOrInterface : JcAnnotatedSymbol, JcAccessible {

    val classpath: JcClasspath

    val fields: List<JcField>
    val methods: List<JcMethod>

    val simpleName: String
    val signature: String?

    val isAnonymous: Boolean

    suspend fun superclass(): JcClassOrInterface?
    suspend fun outerMethod(): JcMethod?
    suspend fun outerClass(): JcClassOrInterface?
    suspend fun interfaces(): List<JcClassOrInterface>
    suspend fun innerClasses(): List<JcClassOrInterface>

}

interface JcAnnotation : JcSymbol {

    val visible: Boolean
    suspend fun jcClass(): JcClassOrInterface?

    suspend fun values(): Map<String, Any?>

    fun matches(className: String): Boolean

}

interface JcMethod : JcSymbol, JcAnnotatedSymbol, JcAccessible {

    /** reference to class */
    val jcClass: JcClassOrInterface

    val description: String

    val returnType: TypeName

    val signature: String?
    val parameters: List<JcParameter>

    suspend fun exceptions(): List<JcClassOrInterface>

    suspend fun body(): MethodNode // match type system
}

interface JcField : JcAnnotatedSymbol, JcAccessible {

    val jcClass: JcClassOrInterface
    val type: TypeName

    val signature: String?
}

interface JcParameter : JcAnnotated, JcAccessible {
    val type: TypeName
    val name: String?
    val index: Int
    val method: JcMethod
}

interface TypeName {
    val typeName: String
}