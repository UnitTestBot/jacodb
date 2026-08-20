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

    private fun archive(vararg entries: Pair<String, String?>): ByteArrayInputStream {
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
        return ByteArrayInputStream(bytes.toByteArray())
    }
}
