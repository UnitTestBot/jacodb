package org.utbot.jcdb.design

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
