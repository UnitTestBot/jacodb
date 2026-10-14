/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jacodb.ets.test

import org.jacodb.ets.dto.BasicBlockDto
import org.jacodb.ets.dto.BodyDto
import org.jacodb.ets.dto.BooleanTypeDto
import org.jacodb.ets.dto.CfgDto
import org.jacodb.ets.dto.ClassDto
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.ConstantDto
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.FileSignatureDto
import org.jacodb.ets.dto.IfStmtDto
import org.jacodb.ets.dto.MethodDto
import org.jacodb.ets.dto.MethodSignatureDto
import org.jacodb.ets.dto.NamespaceDto
import org.jacodb.ets.dto.NamespaceSignatureDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.ReturnVoidStmtDto
import org.jacodb.ets.utils.toDot
import org.jacodb.ets.utils.toText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EtsFileDtoRenderingTest {
    private val fileSignature = FileSignatureDto(projectName = "Test", fileName = "test.ts")

    private fun classDto(name: String, namespace: NamespaceSignatureDto? = null, methods: List<MethodDto> = emptyList()) =
        ClassDto(
            signature = ClassSignatureDto(name = name, declaringFile = fileSignature, declaringNamespace = namespace),
            modifiers = 0,
            decorators = emptyList(),
            superClassName = null,
            implementedInterfaceNames = emptyList(),
            fields = emptyList(),
            methods = methods,
        )

    @Test
    fun `DTO renderers include classes in nested namespaces without DOT ID collisions`() {
        val outerSignature = NamespaceSignatureDto(name = "Outer", declaringFile = fileSignature)
        val innerSignature = NamespaceSignatureDto(
            name = "Inner",
            declaringFile = fileSignature,
            declaringNamespace = outerSignature,
        )
        val dto = EtsFileDto(
            signature = fileSignature,
            namespaces = listOf(
                NamespaceDto(
                    signature = outerSignature,
                    classes = listOf(classDto("C", namespace = outerSignature)),
                    namespaces = listOf(
                        NamespaceDto(
                            signature = innerSignature,
                            classes = listOf(classDto("C", namespace = innerSignature)),
                        ),
                    ),
                ),
            ),
            classes = listOf(classDto("C")),
            importInfos = emptyList(),
            exportInfos = emptyList(),
        )

        val dot = dto.toDot()
        val text = dto.toText()

        assertTrue(dot.contains("\"class:C\" [shape=rect"))
        assertTrue(dot.contains("\"class:Outer.C\" [shape=rect"))
        assertTrue(dot.contains("\"class:Outer.Inner.C\" [shape=rect"))
        assertEquals(3, text.lines().count { it.startsWith("= CLASS") })
    }

    @Test
    fun `DTO DOT separates namespace class ID from equal qualified method name`() {
        val namespaceSignature = NamespaceSignatureDto(name = "A", declaringFile = fileSignature)
        val topClassSignature = ClassSignatureDto(name = "A", declaringFile = fileSignature)
        val method = MethodDto(
            signature = MethodSignatureDto(
                declaringClass = topClassSignature,
                name = "B",
                parameters = emptyList(),
                returnType = NumberTypeDto,
            ),
            modifiers = 0,
            decorators = emptyList(),
        )
        val dto = EtsFileDto(
            signature = fileSignature,
            namespaces = listOf(
                NamespaceDto(
                    signature = namespaceSignature,
                    classes = listOf(classDto("B", namespace = namespaceSignature)),
                ),
            ),
            classes = listOf(classDto("A", methods = listOf(method))),
            importInfos = emptyList(),
            exportInfos = emptyList(),
        )

        val dot = dto.toDot()

        assertTrue(dot.contains("\"class:A.B\" [shape=rect"))
        assertTrue(dot.contains("\"method:A.B\" [shape=oval"))
        assertTrue(dot.contains("\"class:A\" -> \"method:A.B\" [dir=none]"))
    }

    @Test
    fun `DTO DOT labels conditional successors in raw DTO order`() {
        val classSignature = ClassSignatureDto(name = "C", declaringFile = fileSignature)
        val method = MethodDto(
            signature = MethodSignatureDto(
                declaringClass = classSignature,
                name = "f",
                parameters = emptyList(),
                returnType = NumberTypeDto,
            ),
            modifiers = 0,
            decorators = emptyList(),
            body = BodyDto(
                locals = emptyList(),
                cfg = CfgDto(
                    blocks = listOf(
                        BasicBlockDto(
                            id = 0,
                            successors = listOf(1, 2),
                            stmts = listOf(IfStmtDto(condition = ConstantDto(value = "true", type = BooleanTypeDto))),
                        ),
                        BasicBlockDto(id = 1, successors = emptyList(), stmts = listOf(ReturnVoidStmtDto)),
                        BasicBlockDto(id = 2, successors = emptyList(), stmts = listOf(ReturnVoidStmtDto)),
                    ),
                ),
            ),
        )
        val dto = EtsFileDto(
            signature = fileSignature,
            namespaces = emptyList(),
            classes = listOf(classDto("C", methods = listOf(method))),
            importInfos = emptyList(),
            exportInfos = emptyList(),
        )

        val dot = dto.toDot()

        assertTrue(dot.contains("\"method:C.f.bb0.0\" -> \"method:C.f.bb1.0\" [lhead=\"method:C.f.bb1\", label=\"false\"]"))
        assertTrue(dot.contains("\"method:C.f.bb0.0\" -> \"method:C.f.bb2.0\" [lhead=\"method:C.f.bb2\", label=\"true\"]"))
    }
}
