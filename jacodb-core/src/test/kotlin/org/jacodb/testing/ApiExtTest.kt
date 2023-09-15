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

package org.jacodb.testing

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcType
import org.jacodb.api.ext.*
import org.jacodb.testing.hierarchies.Creature
import org.jacodb.testing.hierarchies.Creature.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class ApiExtTest : BaseTest() {

    companion object : WithGlobalDB()

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
        assertTrue(classOf<Dinosaur>() isSubClassOf classOf<Creature>())

        assertFalse(classOf<Dinosaur>() isSubClassOf classOf<Fish>())
        assertTrue(classOf<TRex>() isSubClassOf classOf<Creature>())
        assertTrue(classOf<TRex>() isSubClassOf classOf<Animal>())
        assertTrue(classOf<TRex>() isSubClassOf classOf<DinosaurImpl>())

        assertFalse(classOf<TRex>() isSubClassOf classOf<Fish>())
        assertFalse(classOf<Pterodactyl>() isSubClassOf classOf<Fish>())
        assertTrue(classOf<Pterodactyl>() isSubClassOf classOf<Bird>())
    }

    private inline fun <reified T> typeOf(): JcType {
        return cp.findTypeOrNull<T>() ?: throw IllegalStateException("Type ${T::class.java.name} not found")
    }

    private inline fun <reified T> classOf(): JcClassOrInterface {
        return cp.findClass<T>()
    }
}