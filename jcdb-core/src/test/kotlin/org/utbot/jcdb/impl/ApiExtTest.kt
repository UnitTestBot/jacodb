/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class ApiExtTest : BaseTest() {

    companion object : WithDB()

    @Test
    fun `unboxing primitive type`() {
        val clazz = typeOf<java.lang.Short>()
        assertEquals(cp.short, clazz.unboxIfNeeded())
    }

    @Test
    fun `unboxing regular type`() {
        val clazz = typeOf<String>()
        assertEquals(clazz, clazz.unboxIfNeeded())
    }

    @Test
    fun `autoboxing primitive type`() {
        val type = cp.findTypeOrNull("short")

        assertEquals(typeOf<java.lang.Short>(), type?.autoboxIfNeeded())
    }

    @Test
    fun `autoboxing regular type`() {
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

    private inline fun <reified T> typeOf(): JcType {
        return cp.findTypeOrNull<T>() ?: throw IllegalStateException("Type ${T::class.java.name} not found")
    }

    private inline fun <reified T> classOf(): JcClassOrInterface {
        return cp.findClass<T>()
    }
}