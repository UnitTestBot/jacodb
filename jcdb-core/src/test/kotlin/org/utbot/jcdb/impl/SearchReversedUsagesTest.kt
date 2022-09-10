package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.FieldUsageMode
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.impl.index.Usages
import org.utbot.jcdb.impl.index.reversedUsagesExt
import org.utbot.jcdb.impl.usages.fields.FieldA
import org.utbot.jcdb.impl.usages.fields.FieldB
import org.utbot.jcdb.impl.usages.methods.MethodA
import org.utbot.jcdb.jcdb

class SearchReversedUsagesTest : LibrariesMixin {

    private val db = runBlocking {
        jcdb {
            predefinedDirOrJars = allClasspath
            useProcessJavaRuntime()
            installFeatures(Usages)
        }
    }

    private val cp = runBlocking { db.classpathSet(allClasspath) }

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
                ),
                "hello1" to setOf(
                    "org.utbot.jcdb.impl.usages.methods.MethodB#hoho"
                )
            ),
            usages
        )
    }

    private inline fun <reified T> fieldsUsages(mode: FieldUsageMode = FieldUsageMode.WRITE): Map<String, Set<String>> {
        return runBlocking {
            val classId = cp.findClass<T>()

            val fields = classId.fields()
            val usageExt = cp.reversedUsagesExt

            fields.map {
                it.name to usageExt.findUsages(it, mode).map { it.classId.name + "#" + it.name }.toSortedSet()
            }
                .toMap()
                .filterNot { it.value.isEmpty() }
                .toSortedMap()
        }
    }

    private inline fun <reified T> methodsUsages(): Map<String, Set<String>> {
        return runBlocking {
            val classId = cp.findClass<T>()
            val methods = classId.methods()
            val usageExt = cp.reversedUsagesExt

            methods.map {
                it.name to usageExt.findUsages(it).map { it.classId.name + "#" + it.name }.toSortedSet()
            }
                .toMap()
                .filterNot { it.value.isEmpty() }
                .toSortedMap()
        }
    }

    @AfterEach
    fun cleanup() {
        cp.close()
        db.close()
    }
}