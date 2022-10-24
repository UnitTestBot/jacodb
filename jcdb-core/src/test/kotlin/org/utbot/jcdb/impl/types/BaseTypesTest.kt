package org.utbot.jcdb.impl.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.impl.BaseTest
import org.utbot.jcdb.impl.WithDB

abstract class BaseTypesTest : BaseTest() {

    companion object : WithDB()

    protected suspend inline fun <reified T> findClassType(): JcClassType {
        val found = cp.findTypeOrNull(T::class.java.name)
        assertNotNull(found)
        return found!!.assertIs()
    }

    protected fun JcType?.assertClassType(): JcClassType {
        assertNotNull(this)
        return this!!.assertIs()
    }

    protected suspend inline fun <reified T> JcType?.assertType(): JcClassType {
        val expected = findClassType<T>()
        assertEquals(
            expected.jcClass.name,
            (this as? JcClassType)?.jcClass?.name,
            "Expected ${expected.jcClass.name} but got ${this?.typeName}"
        )
        return this as JcClassType
    }


    protected inline fun <reified T> Any.assertIs(): T {
        return assertInstanceOf(T::class.java, this)
    }
}