package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.FieldUsageMode
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.impl.features.InMemoryHierarchy
import org.utbot.jcdb.impl.features.Usages
import org.utbot.jcdb.impl.features.findUsages
import org.utbot.jcdb.impl.usages.fields.FieldA
import org.utbot.jcdb.impl.usages.fields.FieldB
import org.utbot.jcdb.impl.usages.methods.MethodA
import kotlin.system.measureTimeMillis

abstract class BaseSearchUsagesTest : BaseTest() {

    @Test
    fun `classes read fields`() {
        val usages = fieldsUsages<FieldA>(FieldUsageMode.READ)
        assertEquals(
            sortedMapOf(
                "a" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>",
                    "org.utbot.jcdb.impl.usages.fields.FieldA#isPositive",
                    "org.utbot.jcdb.impl.usages.fields.FieldA#useCPrivate",
                    "org.utbot.jcdb.impl.usages.fields.FieldAImpl#hello"
                ),
                "b" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#isPositive",
                    "org.utbot.jcdb.impl.usages.fields.FieldA#useA",
                ),
                "fieldB" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#useCPrivate",
                )
            ),
            usages
        )
    }

    @Test
    fun `classes write fields`() {
        val usages = fieldsUsages<FieldA>()
        assertEquals(
            sortedMapOf(
                "a" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>",
                    "org.utbot.jcdb.impl.usages.fields.FieldA#useA",
                ),
                "b" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>"
                ),
                "fieldB" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>",
                )
            ),
            usages
        )
    }

    @Test
    fun `classes write fields with rebuild`() {
        val time = measureTimeMillis {
            runBlocking {
                cp.db.rebuildFeatures()
            }
        }
        println("Features rebuild in ${time}ms")
        val usages = fieldsUsages<FieldA>()
        assertEquals(
            sortedMapOf(
                "a" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>",
                    "org.utbot.jcdb.impl.usages.fields.FieldA#useA",
                ),
                "b" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>"
                ),
                "fieldB" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>",
                )
            ),
            usages
        )
    }

    @Test
    fun `classes write fields coupled`() {
        val usages = fieldsUsages<FieldB>()
        assertEquals(
            sortedMapOf(
                "c" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FakeFieldA#useCPrivate",
                    "org.utbot.jcdb.impl.usages.fields.FieldA#useCPrivate",
                    "org.utbot.jcdb.impl.usages.fields.FieldB#<init>",
                    "org.utbot.jcdb.impl.usages.fields.FieldB#useCPrivate",
                )
            ),
            usages
        )
    }

    @Test
    fun `classes methods usages`() {
        val usages = methodsUsages<MethodA>()
        assertEquals(
            sortedMapOf(
                "<init>" to setOf(
                    "org.utbot.jcdb.impl.usages.methods.MethodB#hoho",
                    "org.utbot.jcdb.impl.usages.methods.MethodC#<init>"
                ),
                "hello" to setOf(
                    "org.utbot.jcdb.impl.usages.methods.MethodB#hoho",
                    "org.utbot.jcdb.impl.usages.methods.MethodC#hello",
                )
            ),
            usages
        )
    }

    private inline fun <reified T> fieldsUsages(mode: FieldUsageMode = FieldUsageMode.WRITE): Map<String, Set<String>> {
        return runBlocking {
            val classId = cp.findClass<T>()

            val fields = classId.declaredFields

            fields.associate {
                it.name to cp.findUsages(it, mode).map { it.enclosingClass.name + "#" + it.name }.toSortedSet()
            }.filterNot { it.value.isEmpty() }.toSortedMap()
        }
    }

    private inline fun <reified T> methodsUsages(): Map<String, Set<String>> {
        return runBlocking {
            val classId = cp.findClass<T>()
            val methods = classId.declaredMethods

            methods.map {
                it.name to cp.findUsages(it).map { it.enclosingClass.name + "#" + it.name }.toSortedSet()
            }
                .toMap()
                .filterNot { it.value.isEmpty() }
                .toSortedMap()
        }
    }

}

class InMemoryHierarchySearchUsagesTest : BaseSearchUsagesTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)
}

class SearchUsagesTest : BaseSearchUsagesTest() {
    companion object : WithDB(Usages)
}