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

package org.utbot.jacodb.impl

import com.google.common.cache.AbstractCache
import com.google.common.collect.Iterators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.ext.findClass
import org.utbot.jacodb.api.ext.findClassOrNull
import org.utbot.jacodb.impl.fs.BuildFolderLocation
import org.utbot.jacodb.impl.storage.PersistentLocationRegistry
import java.io.File
import java.nio.file.Files
import java.util.*

class DatabaseLifecycleTest {

    private var db = runBlocking {
        jacodb {
            useProcessJavaRuntime()
        } as JcDatabaseImpl
    }
    private val tempFolder = Files.createTempDirectory("classpath-copy-" + UUID.randomUUID()).toFile()

    private val testDirClone: File get() = File(tempFolder, "test")
    private val guavaLibClone: File get() = File(tempFolder, guavaLib.name)


    @BeforeEach
    fun cloneClasspath() {
        allClasspath.forEach {
            if (it.isFile) {
                it.copyTo(File(tempFolder, it.name))
            } else if (it.absolutePath.replace(File.separator, "/").contains("build/classes/kotlin/test")) {
                // copy only test kotlin resources
                it.copyRecursively(File(tempFolder, it.name), true)
            }
        }
    }

    @Test
    fun `refresh is working when build dir is removed`() = runBlocking {
        val cp = db.classpath(listOf(testDirClone))
        val barKt = cp.findClass<BarKt>()
        db.awaitBackgroundJobs()
        assertTrue(testDirClone.deleteRecursively())
        assertNotNull(barKt.declaredMethods.first().body())

        db.refresh()

        assertEquals(1, cp.locations.filterIsInstance<BuildFolderLocation>().size)
        withRegistry {
            assertEquals(1, snapshots.size)
            assertEquals(
                1,
                actualLocations.filter { it.jcLocation?.createRefreshed() == null }.size
            )
        }

        with(cp.findClass<BarKt>()) {
            assertNotNull(declaredMethods.first().body())
        }

        cp.close()
        db.refresh()
        withRegistry {
            assertTrue(snapshots.isEmpty())
            assertTrue(actualLocations.all { it.jcLocation !is BuildFolderLocation })
        }
    }

    @Test
    fun `method could be read from build dir`() = runBlocking {
        val cp = db.classpath(listOf(testDirClone))
        val barKt = cp.findClass<BarKt>()

        assertNotNull(
            runBlocking {
                barKt.declaredMethods.first().body()
            }
        )
    }

    private fun File.deleteWithRetries(retries: Int): Boolean {
        var result = false
        var counter = 0
        while (!result && counter < retries) {
            result = delete()
            counter++
            if (!result) {
                println("Deletion failed $counter")
                Thread.sleep(1000)
            }
        }
        return result
    }

    @Test
    fun `refresh is working when jar is removed`() = runBlocking {
        val cp = db.classpath(listOf(guavaLibClone))
        val abstractCacheClass = cp.findClass<AbstractCache<*, *>>()
        db.awaitBackgroundJobs() // is required for deleting jar

        assertTrue(guavaLibClone.deleteWithRetries(3))
        assertNotNull(abstractCacheClass.declaredMethods.first().body())

        db.refresh()
        withRegistry {
            assertEquals(1, snapshots.size)
        }

        cp.findClass<AbstractCache<*, *>>()
        cp.close()
        db.refresh()
        withRegistry {
            assertTrue(snapshots.isEmpty())
            assertEquals(db.javaRuntime.allLocations.size, actualLocations.size)
        }
    }

    @Test
    fun `method body could be read from jar`() = runBlocking {
        val cp = db.classpath(listOf(guavaLibClone))
        val abstractCacheClass = cp.findClassOrNull<AbstractCache<*, *>>()
        assertNotNull(abstractCacheClass!!)

        assertNotNull(
            abstractCacheClass.declaredMethods.first().body()
        )
    }

    @Test
    fun `simultaneous access to method body`() = runBlocking {
        db.awaitBackgroundJobs()
        val cps = (1..10).map { db.classpath(listOf(guavaLibClone)) }

        fun JcClasspath.accessMethod() {

            val abstractCacheClass = findClass<AbstractCache<*, *>>()

            assertNotNull(
                abstractCacheClass.declaredMethods.first().body()
            )
        }
        withContext(Dispatchers.IO) {
            cps.map {
                launch {
                    it.accessMethod()
                    it.close()
                }
            }.joinAll()
        }
        db.refresh()

        withRegistry {
            assertTrue(snapshots.isEmpty())
        }
    }

    @Test
    fun `jar should not be blocked after method read`() = runBlocking {
        val cp = db.classpath(listOf(guavaLibClone))
        val clazz = cp.findClass<Iterators>()
        assertNotNull(clazz.declaredMethods.first().body())
        db.awaitBackgroundJobs()
        assertTrue(guavaLibClone.deleteWithRetries(3))
    }

    @AfterEach
    fun cleanup() {
        db.close()
        tempFolder.deleteRecursively()
    }

    private fun withRegistry(action: PersistentLocationRegistry.() -> Unit) {
        (db.locationsRegistry as PersistentLocationRegistry).action()
    }
}