package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.autoboxIfNeeded
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.api.ext.findTypeOrNull
import org.utbot.jcdb.api.isSubtypeOf
import org.utbot.jcdb.api.short
import org.utbot.jcdb.api.unboxIfNeeded
import org.utbot.jcdb.impl.hierarchies.Creature
import org.utbot.jcdb.impl.hierarchies.Creature.Animal
import org.utbot.jcdb.impl.hierarchies.Creature.Bird
import org.utbot.jcdb.impl.hierarchies.Creature.Dinosaur
import org.utbot.jcdb.impl.hierarchies.Creature.DinosaurImpl
import org.utbot.jcdb.impl.hierarchies.Creature.Fish
import org.utbot.jcdb.impl.hierarchies.Creature.Pterodactyl
import org.utbot.jcdb.impl.hierarchies.Creature.TRex
import org.utbot.jcdb.jcdb

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class ApiExtTest : LibrariesMixin {

    companion object : LibrariesMixin {
        var db: JCDB? = runBlocking {
            jcdb {
                loadByteCode(allClasspath)
                useProcessJavaRuntime()
            }.also {
                it.awaitBackgroundJobs()
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            db?.close()
            db = null
        }
    }

    var cp = runBlocking { db!!.classpath(allClasspath) }

    @Test
    fun `unboxing primitive type`() = runBlocking {
        val clazz = typeOf<java.lang.Short>()
        assertEquals(cp.short, clazz.unboxIfNeeded())
    }

    @Test
    fun `unboxing regular type`() = runBlocking {
        val clazz = typeOf<String>()
        assertEquals(clazz, clazz.unboxIfNeeded())
    }

    @Test
    fun `autoboxing primitive type`() = runBlocking {
        val type = cp.findTypeOrNull("short")

        assertEquals(typeOf<java.lang.Short>(), type?.autoboxIfNeeded())
    }

    @Test
    fun `autoboxing regular type`() = runBlocking {
        val clazz = typeOf<String>()
        assertEquals(clazz, clazz.autoboxIfNeeded())
    }

    @Test
    fun `isSubtype for regular classes`() = runBlocking {
        assertTrue(classOf<Dinosaur>() isSubtypeOf classOf<Creature>())

        assertFalse(classOf<Dinosaur>() isSubtypeOf classOf<Fish>())
        assertTrue(classOf<TRex>() isSubtypeOf classOf<Creature>())
        assertTrue(classOf<TRex>() isSubtypeOf classOf<Animal>())
        assertTrue(classOf<TRex>() isSubtypeOf classOf<DinosaurImpl>())

        assertFalse(classOf<TRex>() isSubtypeOf classOf<Fish>())
        assertFalse(classOf<Pterodactyl>() isSubtypeOf classOf<Fish>())
        assertTrue(classOf<Pterodactyl>() isSubtypeOf classOf<Bird>())
    }

    private inline fun <reified T> typeOf(): JcType = runBlocking {
        cp.findTypeOrNull<T>() ?: throw IllegalStateException("Type ${T::class.java.name} not found")
    }

    private inline fun <reified T> classOf(): JcClassOrInterface = runBlocking {
        cp.findClass<T>()
    }

    @AfterEach
    fun close() {
        cp.close()
    }
}