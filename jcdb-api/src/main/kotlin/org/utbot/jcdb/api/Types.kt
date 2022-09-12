package org.utbot.jcdb.api

import org.objectweb.asm.tree.MethodNode

interface JcTypedField {
    val name: String
    val type: JcType

    val jcClass: JcClassOrInterface
    val jcType: JcType
}

interface JcTypedMethod {
    val name: String
    val returnType: JcType
    val parameters: List<JcTypeMethodParameter>
    val parameterization: List<JcType>

    val exceptions: List<JcClassOrInterface>
    val method: JcMethod

    suspend fun body(): MethodNode

    val jcClass: JcClassOrInterface
    val declaringType: JcType
}

interface JcTypeMethodParameter {
    val annotations: List<JcAnnotation>
    val type: JcType
    val name: String?

    val typeMethod: JcTypedMethod

}

interface JcType {
    val typeName: String

    val nullable: Boolean
}

// boolean, int, float, double, void (?) etc
interface JcPrimitiveType : JcType {
    override val nullable: Boolean
        get() = false
}

interface JcRefType : JcType {
    val jcClass: JcClassOrInterface

    val methods: List<JcTypedMethod>
    val fields: List<JcTypedField>

    fun notNullable() : JcRefType
}

// -----------
// Array<T: Any> -> JcArrayType(JcTypeVariable())
// -----------
// Array<Any> -> JcArrayType(JcClassType())
// -----------
// Array<List<T>> -> JcArrayType(JcParametrizedType(JcClassType(), JcTypeVariable()))
interface JcArrayType : JcRefType {
    val elementType: JcType
}

// -----------
// class A<T> -> JcParametrizedType(JcTypeVariable())
interface JcParametrizedType : JcRefType {
    val parameterTypes: List<JcRefType>
}

// java.lang.String -> JcClassType()
interface JcClassType : JcRefType {
    val superType: JcRefType
    val interfaces: JcRefType

    val outerType: JcRefType?
    val outerMethod: JcTypedMethod?

    val innerTypes: List<JcRefType>
}

// -----------
// class A<T> -> JcParametrizedType(JcTypeVariable())
// -----------
// class A : B<T> -> type = JcClassType(), type.superType = JcParametrizedType(JcTypeVariable())
// -----------
// class A<T> {
//      val field: T
// }
// A<T> -> type = JcParametrizedType(JcTypeVariable()), type.fields[0].type = JcTypeVariable("T")
// -----------
interface JcTypeVariable : JcRefType {
    val typeSymbol: String
}


// Enter points:
// * create reference from JcClassOrInterface
// * byte code: no actual information for local variable. But information from fields and methods.

// case:
// no information inside code. abstract value T
open class Case1A<T> {
    var x: T? = null
}

// but we have information from subclasses
class Case1B : Case1A<String>()

// case
// no information about parametrization inside method body
open class Case2 {

    fun smth() {
        val z = Case1A<ByteArray>()
        z.toString()
    }
}

// case 3
// types for methods
open class Case3<T> {

    var x: T? = null

    fun <W> smth(y: W) { // <- y has type here
        y.toString()
    }
}
