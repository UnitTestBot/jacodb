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

package org.jacodb.ets.test

import org.jacodb.ets.utils.BundledFrontendRuntime
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.net.URLClassLoader
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BundledFrontendRuntimeTest {

    @Test
    fun `archive extracts regular files and directories below target`() {
        val target = createTempDirectory("bundled-frontend-extraction").resolve("runtime")

        val script = BundledFrontendRuntime.extract(
            archive(
                "lib/" to null,
                "lib/lib.esnext.d.ts" to "interface ArrayConstructor {}",
                "index.js" to "console.log('frontend')",
            ),
            target,
        )

        assertEquals(target.resolve("index.js"), script)
        assertTrue(target.resolve("lib").isDirectory())
        assertEquals("interface ArrayConstructor {}", target.resolve("lib/lib.esnext.d.ts").readText())
        assertEquals("console.log('frontend')", script.readText())
    }

    @Test
    fun `archive rejects traversal and removes partial target`() {
        val root = createTempDirectory("bundled-frontend-traversal")
        val target = root.resolve("runtime")

        assertFailsWith<IllegalArgumentException> {
            BundledFrontendRuntime.extract(
                archive(
                    "index.js" to "partial",
                    "../escape" to "outside",
                ),
                target,
            )
        }

        assertFalse(target.exists(), "failed extraction must remove the complete target")
        assertFalse(root.resolve("escape").exists(), "an archive entry must never escape the target")
    }

    @Test
    fun `archive without index script fails and removes partial target`() {
        val target = createTempDirectory("bundled-frontend-missing-script").resolve("runtime")

        val error = assertFailsWith<FileNotFoundException> {
            BundledFrontendRuntime.extract(
                archive("lib.d.ts" to "interface ArrayConstructor {}"),
                target,
            )
        }

        assertTrue(error.message.orEmpty().contains("index.js"))
        assertFalse(target.exists(), "an incomplete runtime must be deleted immediately")
    }

    @Test
    fun `production bundle provides the frontend script`() {
        val script = assertNotNull(BundledFrontendRuntime.script)

        assertTrue(script.exists())
        assertEquals("index.js", script.name)
        assertSame(script, BundledFrontendRuntime.script, "the runtime must be extracted only once")
    }

    @Test
    fun `failed script initialization is memoized without retrying extraction`() {
        FailingRuntimeClassLoader(
            BundledFrontendRuntime::class.java.protectionDomain.codeSource.location,
            BundledFrontendRuntime::class.java.classLoader,
            archiveBytes("lib.d.ts" to "interface ArrayConstructor {}"),
        ).use { loader ->
            val runtimeClass = loader.loadClass(BUNDLED_RUNTIME_CLASS)
            val runtime = runtimeClass.getField("INSTANCE").get(null)
            val scriptGetter = runtimeClass.getMethod("getScript")

            val firstFailure = assertFailsWith<InvocationTargetException> {
                scriptGetter.invoke(runtime)
            }
            val secondFailure = assertFailsWith<InvocationTargetException> {
                scriptGetter.invoke(runtime)
            }

            assertEquals(1, loader.runtimeResourceRequests, "failed initialization must not retry extraction")
            assertSame(firstFailure.cause, secondFailure.cause, "the original initialization failure must be memoized")
        }
    }

    private fun archive(vararg entries: Pair<String, String?>): ByteArrayInputStream {
        return ByteArrayInputStream(archiveBytes(*entries))
    }

    private fun archiveBytes(vararg entries: Pair<String, String?>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { archive ->
            for ((name, contents) in entries) {
                archive.putNextEntry(ZipEntry(name))
                if (contents != null) {
                    archive.write(contents.toByteArray())
                }
                archive.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private class FailingRuntimeClassLoader(
        mainClasses: URL,
        parent: ClassLoader,
        private val runtimeArchive: ByteArray,
    ) : URLClassLoader(arrayOf(mainClasses), parent) {
        var runtimeResourceRequests: Int = 0
            private set

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name != BUNDLED_RUNTIME_CLASS) {
                return super.loadClass(name, resolve)
            }
            return synchronized(getClassLoadingLock(name)) {
                val loadedClass = findLoadedClass(name) ?: findClass(name)
                if (resolve) {
                    resolveClass(loadedClass)
                }
                loadedClass
            }
        }

        override fun getResourceAsStream(name: String): InputStream? {
            if (name == BUNDLED_RUNTIME_RESOURCE) {
                runtimeResourceRequests++
                return ByteArrayInputStream(runtimeArchive)
            }
            return super.getResourceAsStream(name)
        }
    }

    companion object {
        private const val BUNDLED_RUNTIME_CLASS = "org.jacodb.ets.utils.BundledFrontendRuntime"
        private const val BUNDLED_RUNTIME_RESOURCE = "ets-frontend/runtime.zip"
    }
}
