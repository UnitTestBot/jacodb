package org.utbot.jcdb.api

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

interface JcClassOrInterface : JcAnnotatedSymbol, JcAccessible {

    val classpath: JcClasspath

    val fields: List<JcField>
    val methods: List<JcMethod>

    val simpleName: String
    val signature: String?
    val isAnonymous: Boolean

    fun bytecode(): ClassNode

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

    fun exceptions(): List<JcClassOrInterface>

    fun body(): MethodNode // match type system
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