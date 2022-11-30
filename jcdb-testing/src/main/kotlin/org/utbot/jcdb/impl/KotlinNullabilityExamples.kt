package org.utbot.jcdb.impl

class KotlinNullabilityExamples {
    class SomeContainer<E>(
        val nullableProperty: E?,
        val notNullProperty: E,
        val listOfNullable: List<E?>,
        val listOfNotNull: List<E>
    )

    fun SomeContainer<SomeContainer<Int>>.simpleGenerics(
        matrixOfNullable: SomeContainer<SomeContainer<Int?>>,
        containerOfNotNullContainers: SomeContainer<SomeContainer<Int>?>
    ) = Unit

    fun genericsWithProjection(covariant: SomeContainer<out String?>, contravariant: SomeContainer<in String>) = Unit

    fun <T> typeVariableParameters(notNull: T, nullable: T?) = Unit

    fun <A: List<Int?>, B: List<Int>?> typeVariableDeclarations() = Unit

//    fun <T: String?> instantiatedContainer(a: SomeContainer<T>, b: SomeContainer<T>): String? {
//        return a.notNullProperty
//    }
    fun instantiatedContainer(a: SomeContainer<String>, b: SomeContainer<String?>): String? {
        return a.notNullProperty
    }

}