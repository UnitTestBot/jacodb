package org.jacodb.ets.test

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.MethodDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.dto.toEtsMethod
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EtsTsStrictModeTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `lexical strictness survives frontend serialization and model conversion`() {
        val source = directory.resolve("strict-mode.ts")
        source.writeText(
            """
            function sloppy() { return 1; }
            function strictCaller() { "use strict"; return sloppy(); }
            class Box { method() { return 2; } }
            """.trimIndent()
        )
        val output = generateEtsIR(source, isProject = false, timeout = null, provider = EtsIrProvider.TS_FRONTEND)
        val dto = EtsFileDto.loadFromJson(output.readText())
        val serialized = Json { serializersModule = dtoModule }.encodeToString(dto)

        val roundTripped = EtsFileDto.loadFromJson(serialized)
        val methods = roundTripped.toEtsFile().classes.flatMap { it.methods }

        assertEquals(false, methods.single { it.name == "sloppy" }.isStrictMode)
        assertEquals(true, methods.single { it.name == "strictCaller" }.isStrictMode)
        assertEquals(true, methods.single { it.name == "method" }.isStrictMode)
        assertTrue(methods.all { it.isStrictMode != null })
    }

    @Test
    fun `missing metadata remains unknown for bodyful and bodyless legacy methods`() {
        val source = directory.resolve("legacy.ts")
        source.writeText("export function run() { return 1; }")
        val output = generateEtsIR(source, isProject = false, timeout = null, provider = EtsIrProvider.TS_FRONTEND)
        val dto = EtsFileDto.loadFromJson(output.readText())
        val method = dto.classes.flatMap { it.methods }.single { it.signature.name == "run" }
        val legacyMethod = method.copy(isStrictMode = null)
        val serialized = Json { serializersModule = dtoModule }.encodeToString(legacyMethod)

        val decoded = Json { serializersModule = dtoModule }.decodeFromString<MethodDto>(serialized)

        assertNull(decoded.isStrictMode)
        assertNull(decoded.toEtsMethod().isStrictMode)
        assertNull(decoded.copy(body = null).toEtsMethod().isStrictMode)
        assertEquals(true, method.copy(body = null).toEtsMethod().isStrictMode)
    }
}
