package org.utbot.jcdb.impl

class KotlinNullabilityExamples {
    class SomeContainer<E>(
        val nullableProperty: E?,
        val notNullProperty: E
    )

    fun simpleGenerics(
        matrixOfNotNull: SomeContainer<SomeContainer<Int>>,
        matrixOfNullable: SomeContainer<SomeContainer<Int?>>,
        containerOfNotNullContainers: SomeContainer<SomeContainer<Int>?>
    ) = Unit

    fun genericsWithProjection(covariant: SomeContainer<out String?>, contravariant: SomeContainer<in String>) = Unit

    fun <T> typeVariableParameters(notNull: T, nullable: T?) = Unit

    fun <A: List<Int?>, B: List<Int>?> typeVariableDeclarations() = Unit

    fun instantiatedContainer(a: SomeContainer<String>, b: SomeContainer<String?>): String {
        return a.notNullProperty
    }
}