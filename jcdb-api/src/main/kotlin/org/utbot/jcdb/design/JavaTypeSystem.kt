package org.utbot.jcdb.design


// -------- symbols
class JcSymbol(val location:JcDeclaration) {}

class JcBytecodeLocation {
    // cvc
    // jar or file
}

class JcDeclaration {
    // bytecodeLocation
    // offset
}

class JcClassOrInterface {} //Symbol
//JcParametrizedType getType()

class JcParameter {
//type JcType
}

class JcMethod {
    //returnType: JcType
    //parameters: JcParameter
    //declared exceptions: JcExceptionTypes
} //Symbol

class JcField {} //Symbol

// types ---------------------


open class JcType

open class JcPrimitiveType : JcType()


open class JcRefType : JcType()

open class JcArrayType() : JcRefType()


open class JcParametrizedType : JcRefType() { //List<String> -> List
    // T1 -> S1, T2 -> S2
}

open class TypeVariable() : JcRefType()

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








