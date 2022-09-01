package org.utbot.jcdb.api

import org.objectweb.asm.tree.MethodNode
import java.io.File


interface JcBytecodeLocation {
    val jarOrFolder: File
    val hash: String //cvc
}

interface JcDeclaration {
    val location: JcBytecodeLocation
    val relativePath: String // relative to `location` path for declaration
}

interface JcSymbol {
    val name: String
}

interface JcAnnotated {
    val annotations: List<JcAnnotation>
    val declaration: JcDeclaration
}

interface JcAnnotatedSymbol : JcSymbol, JcAnnotated

interface JcClassOrInterface : JcAnnotatedSymbol {

    val fields: List<JcField>
    val methods: List<JcMethod>

    val signature: String?
}

interface JcParameter : JcAnnotated {
    val type: JcType
    val name: String?
    val index: Int
}

interface JcAnnotation : JcSymbol {

    val visible: Boolean
    val jcClass: JcClassOrInterface?

    val values: Map<String, Any?>

}

interface JcMethod : JcSymbol, JcAnnotatedSymbol {

    /** reference to class */
    val jcClass: JcClassOrInterface

    val returnType: JcType

    val signature: String?
    val parameters: List<JcParameter>

    val exceptions: List<JcClassOrInterface>

    suspend fun body(): MethodNode // match type system
}

interface JcField : JcAnnotatedSymbol {

    val declaringClass: JcClassOrInterface
    val fieldClass: JcClassOrInterface

    val signature: String?
}

