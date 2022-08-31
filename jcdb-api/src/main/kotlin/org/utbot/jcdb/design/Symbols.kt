package org.utbot.jcdb.design

import java.io.File


interface JcBytecodeLocation {
    // cvc
    // jar or file
    val jarOrFolder: File
    val cvc: String
}

interface JcDeclaration {
    val location: JcBytecodeLocation
    val path: String
    // bytecodeLocation
    // offset
}

interface JcSymbol {
    val location: JcDeclaration
}

interface JcClassOrInterface : JcSymbol {
    val annotations: List<JcAnnotation>
    val name: String
}

interface JcParameter : JcSymbol {
    val type: JcType
    val annotations: List<JcAnnotation>
}

interface JcAnnotation: JcSymbol {

    val visible: Boolean
    val jcClassName: String
    val jcClass: JcClassOrInterface?

    val values: Map<String, Any?>

}

interface JcMethod : JcSymbol {
    //returnType: JcType
    //parameters: JcParameter
    //declared exceptions: JcExceptionTypes

    /** method name */
    val name: String

    /** reference to class */
    val jcClass: JcClassOrInterface

    val returnType: JcType

    val parameters: List<JcParameter>

    val declaredExceptions: List<JcClassOrInterface>

}
