package org.utbot.jcdb.design

import java.io.File

// -------- symbols -> persisted

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

interface JcParameter {
    val type: JcType
    val annotations: List<JcAnnotation>
}

interface JcAnnotation {

    val visible: Boolean
    val className: String
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

interface JcField : JcSymbol {
    val jcClass: JcClassOrInterface
    val name: String
    val type: JcType
}

// types ---------------------

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

//typeBound?

// relations ---------------------------------------------------------

// SymbolId SymbolId RelationKind (enum)
// classOrInterface -> methods.   Symbol -> Symbol
// class -> extdends/implements -> class A : Comparable<A>  --- details
// class -> nested -> class --- details

// classpath - context
// A.class : A extends B
// B.class (x.jar) :
// B.class (y.jar)

//symbolId + classpath -> Symbol


// ------------------  Indexes


//isValid
//setIn: field -> { method1, method2- }
// loc1 -> method1, loc2 -> method2, loc3 -> field

//onLocRemoved:

// classpath : {loc1, loc2, loc3}   classpath: {loc1, loc3}
