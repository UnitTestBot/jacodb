package org.utbot.jcdb.impl

class NullableExamples(private val refNullable: String?, val refNotNull: String) {
    var primitiveNullable: Int? = null
    var primitiveNotNull: Int = 0

    fun nullableMethod(nullableParam: String?, notNullParam: String): String? = null

    fun notNullMethod(nullableParam: String?, notNullParam: String): String = "dumb return value"
}