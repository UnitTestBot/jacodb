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

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jacodb.ets.dto.ConstantDto
import org.jacodb.ets.dto.LiteralTypeDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsConstant
import org.jacodb.ets.model.EtsNumberConstant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EtsNonFiniteNumberTest {

    @Test
    fun `converts non-finite number constants`() {
        val overflowing = assertIs<EtsNumberConstant>(ConstantDto("1e999", NumberTypeDto).toEtsConstant())
        val infinity = assertIs<EtsNumberConstant>(ConstantDto("Infinity", NumberTypeDto).toEtsConstant())
        val notANumber = assertIs<EtsNumberConstant>(ConstantDto("NaN", NumberTypeDto).toEtsConstant())

        assertEquals(Double.POSITIVE_INFINITY, overflowing.value)
        assertEquals(Double.POSITIVE_INFINITY, infinity.value)
        assertTrue(notANumber.value.isNaN())
    }

    @Test
    fun `rejects null primitive literals because their original kind is unknowable`() {
        val json = Json { serializersModule = dtoModule }

        assertFailsWith<SerializationException> {
            json.decodeFromString<LiteralTypeDto>("""{"literal":null}""")
        }
    }
}
