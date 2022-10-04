package org.utbot.jcdb.impl.types

abstract class PrimitiveAndArrays {

    private val int: Int = 0
    private val intArray: IntArray = IntArray(1)

    abstract fun run(stringArray: Array<String>): IntArray
}