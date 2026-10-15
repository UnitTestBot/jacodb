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

import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsCopyDataPropertiesStmt
import org.jacodb.ets.model.EtsDefineAccessorStmt
import org.jacodb.ets.model.EtsDefineDataPropertyStmt
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNamespace
import org.jacodb.ets.model.EtsNamespaceSignature
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.utils.InterproceduralCfg
import org.jacodb.ets.utils.toDot
import org.jacodb.ets.utils.toHighlightedDotWithCalls
import org.jacodb.ets.utils.toText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class EtsModelRenderingTest {

    @Test
    fun `model file renderers include nested classes and distinct overload nodes`() {
        val fileSignature = EtsFileSignature(projectName = "Project", fileName = "sample.ts")
        val rootClassSignature = EtsClassSignature(name = "Same", file = fileSignature)
        val namespaceSignature = EtsNamespaceSignature(name = "Nested", file = fileSignature)
        val nestedClassSignature = EtsClassSignature(
            name = "Same",
            file = fileSignature,
            namespace = namespaceSignature,
        )
        val noArg = method(rootClassSignature, name = "run")
        val oneArg = method(
            rootClassSignature,
            name = "run",
            parameters = listOf(EtsMethodParameter(index = 0, name = "value", type = EtsAnyType)),
        )
        val nestedMethod = method(nestedClassSignature, name = "run")
        cfg(noArg, listOf(EtsReturnStmt(location = EtsStmtLocation(method = noArg, index = 0), returnValue = null)))
        cfg(oneArg, listOf(EtsReturnStmt(location = EtsStmtLocation(method = oneArg, index = 0), returnValue = null)))
        val rootClass = EtsClassImpl(
            signature = rootClassSignature,
            fields = emptyList(),
            methods = listOf(noArg, oneArg),
        )
        val nestedClass = EtsClassImpl(
            signature = nestedClassSignature,
            fields = emptyList(),
            methods = listOf(nestedMethod),
        )
        val namespace = EtsNamespace(
            signature = namespaceSignature,
            classes = listOf(nestedClass),
            namespaces = emptyList(),
        )
        val file = EtsFile(
            signature = fileSignature,
            classes = listOf(rootClass),
            namespaces = listOf(namespace),
        )

        val dot = file.toDot()
        val text = file.toText()

        assertContains(dot, "\"class:0\" [shape=box")
        assertContains(dot, "\"class:1\" [shape=box")
        assertContains(dot, "\"method:0:0\" [shape=oval")
        assertContains(dot, "\"method:0:1\" [shape=oval")
        assertContains(dot, "\"method:1:0\" [shape=oval")
        assertContains(dot, "\"stmt:0:0:0\" [shape=box")
        assertContains(dot, "\"stmt:0:1:0\" [shape=box")
        assertContains(dot, "Nested::Same")
        assertContains(dot, "Same::run(value: any)")
        assertEquals(2, Regex("= CLASS '").findAll(text).count())
        assertContains(text, "Nested::Same")
    }

    @Test
    fun `CFG renderers show supported object operations`() {
        val fileSignature = EtsFileSignature(projectName = "Project", fileName = "sample.ts")
        val classSignature = EtsClassSignature(name = "Sample", file = fileSignature)
        val method = method(classSignature, name = "objects")
        val target = EtsLocal(name = "target")
        val key = EtsStringConstant(value = "key")
        val statements: List<EtsStmt> = listOf(
            EtsDefineDataPropertyStmt(
                location = EtsStmtLocation(method = method, index = 0),
                target = target,
                key = key,
                value = EtsStringConstant(value = "value"),
            ),
            EtsCopyDataPropertiesStmt(
                location = EtsStmtLocation(method = method, index = 1),
                target = target,
                source = target,
                excludedKeys = emptyList(),
                throwOnNullishSource = false,
            ),
            EtsDefineAccessorStmt(
                location = EtsStmtLocation(method = method, index = 2),
                target = target,
                key = key,
                getter = target,
            ),
        )
        val cfg = cfg(method, statements)

        val blockDot = cfg.toDot()
        val linearDot = cfg.linear.toDot()
        val interproceduralDot = InterproceduralCfg(main = cfg, callees = emptyMap())
            .toHighlightedDotWithCalls(pathStmts = emptySet(), currentStmt = null)

        for (label in listOf("defineDataProperty", "copyDataProperties", "defineAccessor")) {
            assertContains(blockDot, label)
            assertContains(linearDot, label)
            assertContains(interproceduralDot, label)
        }
    }

    @Test
    fun `quoted and HTML labels encode source text once`() {
        val fileSignature = EtsFileSignature(projectName = "Project", fileName = "sample.ts")
        val classSignature = EtsClassSignature(name = "Sa\"mple", file = fileSignature)
        val method = method(classSignature, name = "str\\ings")
        val value = EtsStringConstant(value = "a\\b\"c\r\nd")
        val statement = EtsAssignStmt(
            location = EtsStmtLocation(method = method, index = 0),
            lhv = EtsLocal(name = "x"),
            rhv = value,
        )
        val cfg = cfg(method, listOf(statement))
        val file = EtsFile(
            signature = fileSignature,
            classes = listOf(EtsClassImpl(signature = classSignature, fields = emptyList(), methods = listOf(method))),
            namespaces = emptyList(),
        )

        val linearDot = cfg.linear.toDot()
        val plainBlockDot = cfg.toDot(useHtml = false)
        val htmlBlockDot = cfg.toDot(useHtml = true)
        val modelDot = file.toDot()
        val interprocedural = InterproceduralCfg(main = cfg, callees = emptyMap())
        val plainInterproceduralDot = interprocedural.toHighlightedDotWithCalls(
            pathStmts = emptySet(),
            currentStmt = null,
            useHtml = false,
        )
        val htmlInterproceduralDot = interprocedural.toHighlightedDotWithCalls(
            pathStmts = emptySet(),
            currentStmt = null,
            useHtml = true,
        )

        val quotedFragment = "a\\\\b\\\"c\\r\\nd"
        for (dot in listOf(linearDot, plainBlockDot, modelDot, plainInterproceduralDot)) {
            assertContains(dot, quotedFragment)
        }
        assertContains(modelDot, "Sa\\\"mple")
        assertContains(modelDot, "str\\\\ings")
        val htmlFragment = "a\\b&quot;c"
        assertContains(htmlBlockDot, htmlFragment)
        assertContains(htmlInterproceduralDot, htmlFragment)
    }

    private fun method(
        classSignature: EtsClassSignature,
        name: String,
        parameters: List<EtsMethodParameter> = emptyList(),
    ): EtsMethodImpl {
        return EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classSignature,
                name = name,
                parameters = parameters,
                returnType = EtsAnyType,
            ),
        )
    }

    private fun cfg(method: EtsMethodImpl, statements: List<EtsStmt>): EtsBlockCfg {
        val cfg = EtsBlockCfg(
            blocks = listOf(BasicBlock(id = 0, statements = statements)),
            successors = mapOf(0 to emptyList()),
        )
        method.body.cfg = cfg
        return cfg
    }
}
