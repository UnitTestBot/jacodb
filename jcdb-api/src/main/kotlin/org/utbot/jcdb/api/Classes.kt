package org.utbot.jcdb.api

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

interface JcClassOrInterface : JcAnnotatedSymbol, JcAccessible {

    val classpath: JcClasspath

    val declaredFields: List<JcField>
    val declaredMethods: List<JcMethod>

    val simpleName: String
    val signature: String?
    val isAnonymous: Boolean

    fun bytecode(): ClassNode
    fun binaryBytecode(): ByteArray

    val superClass: JcClassOrInterface?
    val outerMethod: JcMethod?
    val outerClass: JcClassOrInterface?
    val interfaces: List<JcClassOrInterface>
    val innerClasses: List<JcClassOrInterface>

}

interface JcAnnotation : JcSymbol {

    val visible: Boolean
    val jcClass: JcClassOrInterface?

    val values: Map<String, Any?>

    fun matches(className: String): Boolean

}

interface JcMethod : JcSymbol, JcAnnotatedSymbol, JcAccessible {

    /** reference to class */
    val enclosingClass: JcClassOrInterface

    val description: String

    val returnType: TypeName

    val signature: String?
    val parameters: List<JcParameter>

    val exceptions: List<JcClassOrInterface>

    fun body(): MethodNode
}

interface JcField : JcAnnotatedSymbol, JcAccessible {

    val enclosingClass: JcClassOrInterface
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