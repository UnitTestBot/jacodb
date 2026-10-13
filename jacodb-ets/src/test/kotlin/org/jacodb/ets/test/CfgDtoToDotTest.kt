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

import kotlin.test.assertTrue
import org.jacodb.ets.dto.BasicBlockDto
import org.jacodb.ets.dto.BigIntTypeDto
import org.jacodb.ets.dto.BooleanTypeDto
import org.jacodb.ets.dto.CfgDto
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.ConstantDto
import org.jacodb.ets.dto.CopyDataPropertiesStmtDto
import org.jacodb.ets.dto.DefineAccessorStmtDto
import org.jacodb.ets.dto.DefineDataPropertyStmtDto
import org.jacodb.ets.dto.ExceptionalSuccessorDto
import org.jacodb.ets.dto.FileSignatureDto
import org.jacodb.ets.dto.IfStmtDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.NewArrayExprDto
import org.jacodb.ets.dto.NewClassExprDto
import org.jacodb.ets.dto.NewExprDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.PropertyRefDto
import org.jacodb.ets.dto.RequireObjectCoercibleExprDto
import org.jacodb.ets.dto.ReturnStmtDto
import org.jacodb.ets.dto.ReturnVoidStmtDto
import org.jacodb.ets.dto.SpreadExpansionExprDto
import org.jacodb.ets.dto.StringTypeDto
import org.jacodb.ets.dto.SymbolTypeDto
import org.jacodb.ets.dto.TemplateObjectExprDto
import org.jacodb.ets.dto.ThrowStmtDto
import org.jacodb.ets.dto.ToNumericExprDto
import org.jacodb.ets.dto.ToPropertyKeyExprDto
import org.jacodb.ets.dto.UnknownTypeDto
import org.jacodb.ets.dto.ValueDto
import org.jacodb.ets.utils.toDot
import org.junit.jupiter.api.Test

class CfgDtoToDotTest {
    @Test
    fun `labels condition successors using the DTO false then true convention`() {
        val cfg = CfgDto(blocks = listOf(
            BasicBlockDto(
                id = 0,
                successors = listOf(1, 2),
                stmts = listOf(IfStmtDto(condition = ConstantDto(value = "true", type = BooleanTypeDto))),
            ),
            BasicBlockDto(id = 1, successors = emptyList(), stmts = listOf(ReturnVoidStmtDto)),
            BasicBlockDto(id = 2, successors = emptyList(), stmts = listOf(ReturnVoidStmtDto)),
        ))

        for (useHtml in listOf(false, true)) {
            val dot = cfg.toDot(useHtml = useHtml)

            assertTrue("0 -> 2 [label=\"true\"]" in dot)
            assertTrue("0 -> 1 [label=\"false\"]" in dot)
        }
    }

    @Test
    fun `retains exceptional edges of blocks without normal successors`() {
        val cfg = CfgDto(blocks = listOf(
            BasicBlockDto(
                id = 0,
                successors = emptyList(),
                exceptionalSuccessors = listOf(ExceptionalSuccessorDto(stmtIndex = 0, target = 1)),
                stmts = listOf(ThrowStmtDto(arg = ConstantDto(value = "failure", type = StringTypeDto))),
            ),
            BasicBlockDto(id = 1, successors = emptyList(), stmts = listOf(ReturnVoidStmtDto)),
        ))

        val dot = cfg.toDot(useHtml = true)

        assertTrue("0 -> 1 [label=\"exception @0\" style=dashed]" in dot)
    }

    @Test
    fun `plain labels preserve literal backslashes quotes and control characters`() {
        val cfg = returning(
            ConstantDto(value = "\\N\\l\\", type = StringTypeDto),
            ConstantDto(value = "\"\n\r\t\u0000", type = StringTypeDto),
        )

        val dot = cfg.toDot(useHtml = false)

        assertTrue("""return \"\\\\N\\\\l\\\\\"\l""" in dot)
        assertTrue("""return \"\\\"\\n\\r\\t\\u0000\"\l""" in dot)
    }

    @Test
    fun `HTML labels preserve literal backslashes before quotes and escape markup`() {
        val cfg = returning(ConstantDto(value = "\\\"<&>", type = StringTypeDto))

        val dot = cfg.toDot(useHtml = true)

        assertTrue("""return &quot;\\\&quot;&lt;&amp;&gt;&quot;<br/>""" in dot)
    }

    @Test
    fun `renders current lowering operations with their operands and parameters`() {
        val local = LocalDto(name = "value", type = UnknownTypeDto)
        val signature = ClassSignatureDto(
            name = "Created",
            declaringFile = FileSignatureDto(projectName = "test", fileName = "test.ts"),
        )
        val cfg = returning(
            SpreadExpansionExprDto(iterable = local, expectedCount = 2),
            ToPropertyKeyExprDto(arg = local),
            ToNumericExprDto(arg = local, type = NumberTypeDto),
            ToNumericExprDto(arg = local, type = BigIntTypeDto),
            NewArrayExprDto(elementType = SymbolTypeDto, size = ConstantDto(value = "2", type = NumberTypeDto)),
            NewClassExprDto(signature = signature),
            NewExprDto(classType = UnknownTypeDto, constructorValue = local),
            RequireObjectCoercibleExprDto(arg = local, type = UnknownTypeDto),
            PropertyRefDto(
                instance = local,
                key = ConstantDto(value = "name", type = StringTypeDto),
                type = UnknownTypeDto,
            ),
        )

        val dot = cfg.toDot(useHtml = false)

        val expectedLabels = listOf(
            "expand_spread(value, expectedCount=2)",
            "to_property_key(value)",
            "to_numeric(value): number",
            "to_numeric(value): bigint",
            "new Array<symbol>(2)",
            "new_class Created",
            "new unknown via value",
            "require_object_coercible(value)",
            """value[\"name\"]""",
        )
        for (label in expectedLabels) {
            assertTrue(label in dot, message = label)
        }
    }

    @Test
    fun `template labels distinguish absent cooked text from raw escapes`() {
        val cfg = returning(TemplateObjectExprDto(
            siteId = "site",
            cooked = listOf(null, "text"),
            raw = listOf("\\u{}", "text"),
            type = UnknownTypeDto,
        ))

        val dot = cfg.toDot(useHtml = true)

        val expectedLabel = """template_object(site=&quot;site&quot;, cooked=[undefined, &quot;text&quot;], """ +
            """raw=[&quot;\\u{}&quot;, &quot;text&quot;])"""
        assertTrue(expectedLabel in dot)
    }

    @Test
    fun `property definition labels retain exclusions getter and nullish policy`() {
        val target = LocalDto(name = "target", type = UnknownTypeDto)
        val source = LocalDto(name = "source", type = UnknownTypeDto)
        val key = LocalDto(name = "key", type = SymbolTypeDto)
        val getter = LocalDto(name = "getter", type = UnknownTypeDto)
        val cfg = CfgDto(blocks = listOf(BasicBlockDto(
            id = 0,
            successors = emptyList(),
            stmts = listOf(
                DefineDataPropertyStmtDto(target = target, key = key, value = source),
                DefineAccessorStmtDto(target = target, key = key, getter = getter),
                CopyDataPropertiesStmtDto(
                    target = target, source = source, excludedKeys = listOf(key), throwOnNullishSource = true,
                ),
                CopyDataPropertiesStmtDto(
                    target = target, source = source, excludedKeys = emptyList(), throwOnNullishSource = false,
                ),
            ),
        )))

        val dot = cfg.toDot(useHtml = false)

        assertTrue("define_data_property(target, key, source)" in dot)
        assertTrue("define_accessor(target, key, getter=getter)" in dot)
        assertTrue("copy_data_properties(target, source, excluded=[key], throwOnNullishSource=true)" in dot)
        assertTrue("copy_data_properties(target, source, excluded=[], throwOnNullishSource=false)" in dot)
    }

    private fun returning(vararg values: ValueDto): CfgDto = CfgDto(blocks = listOf(
        BasicBlockDto(id = 0, successors = emptyList(), stmts = values.map { ReturnStmtDto(arg = it) }),
    ))
}
