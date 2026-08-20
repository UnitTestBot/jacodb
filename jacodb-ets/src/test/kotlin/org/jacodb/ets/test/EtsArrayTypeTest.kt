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

import org.jacodb.ets.dto.ArrayTypeDto
import org.jacodb.ets.dto.ConstantDto
import org.jacodb.ets.dto.NewArrayExprDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.toEtsType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsNumberType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EtsArrayTypeTest {
    @Test
    fun `converts deeply nested DTO array dimensions`() {
        val type = ArrayTypeDto(ArrayTypeDto(ArrayTypeDto(NumberTypeDto, 1), 1), 2)

        assertEquals(EtsArrayType(EtsNumberType, 4), type.toEtsType())
    }

    @Test
    fun `DTO new array expression flattens deeply nested array dimensions`() {
        val expression = NewArrayExprDto(
            elementType = ArrayTypeDto(ArrayTypeDto(ArrayTypeDto(NumberTypeDto, 1), 1), 1),
            size = ConstantDto("2", NumberTypeDto),
        )

        assertEquals(ArrayTypeDto(NumberTypeDto, 4), expression.type)
    }

    @Test
    fun `model new array expression flattens deeply nested array dimensions`() {
        val expression = EtsNewArrayExpr(
            elementType = EtsArrayType(EtsArrayType(EtsArrayType(EtsNumberType, 1), 1), 1),
            size = EtsNumberConstant(2.0),
        )

        assertEquals(EtsArrayType(EtsNumberType, 4), expression.type)
    }
}
