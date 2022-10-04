package org.utbot.jcdb.impl.types

//open class Foo<T> {
//
//    var state: T? = null
//
//    fun <W : T> useState(w: W) {
//        state = w
//    }
//
//}
//
//
//class Bar : Foo<String>()
//
//val cp: JcClasspath get() = TODO()
//
//fun main() = runBlocking {
//    val barType = cp.findTypeOrNull("org.utbot.jcdb.impl.generics.Bar")!!
//    barType as JcClassType
//    val fooType = barType.superType() as JcParametrizedType
//    val stateField = fooType.fields.first { it.name == "state" }
//
//    // what we expect
//    stateField.fieldType == cp.findTypeOrNull("java.lang.String")
//}
