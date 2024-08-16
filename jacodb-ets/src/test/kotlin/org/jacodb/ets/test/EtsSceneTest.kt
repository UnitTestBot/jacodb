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

import org.jacodb.ets.base.EtsArrayType
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsCallStmt
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsInstLocation
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsVoidType
import org.jacodb.ets.graph.EtsApplicationGraphImpl
import org.jacodb.ets.graph.EtsCfg
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFieldSubSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import kotlin.test.Test
import kotlin.test.assertEquals

class EtsSceneTest {

    @Test
    fun `test create EtsScene with multiple files`() {
        val fileCatSignature = EtsFileSignature(
            projectName = "TestProject",
            fileName = "cat.ts",
        )
        val classCatSignature = EtsClassSignature(
            name = "Cat",
            file = fileCatSignature,
            namespace = null,
        )
        val fieldName = EtsFieldImpl(
            signature = EtsFieldSignature(
                enclosingClass = classCatSignature,
                sub = EtsFieldSubSignature(
                    name = "name",
                    type = EtsStringType,
                )
            )
        )
        val methodMeow = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classCatSignature,
                name = "meow",
                parameters = emptyList(),
                returnType = EtsVoidType,
            )
        )
        val ctorCat = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classCatSignature,
                name = "constructor",
                parameters = emptyList(),
                returnType = EtsVoidType,
            ),
        )
        val classCat = EtsClassImpl(
            signature = classCatSignature,
            fields = listOf(fieldName),
            methods = listOf(methodMeow),
            ctor = ctorCat,
        )
        val fileCat = EtsFile(
            signature = fileCatSignature,
            classes = listOf(classCat),
            namespaces = emptyList(),
        )

        val fileBoxSignature = EtsFileSignature(
            projectName = "TestProject",
            fileName = "box.ts",
        )
        val classBoxSignature = EtsClassSignature(
            name = "Box",
            file = fileBoxSignature,
            namespace = null,
        )
        val fieldCats = EtsFieldImpl(
            signature = EtsFieldSignature(
                enclosingClass = classBoxSignature,
                sub = EtsFieldSubSignature(
                    name = "cats",
                    type = EtsArrayType(EtsClassType(classCatSignature), 1),
                )
            )
        )
        val methodTouch = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classBoxSignature,
                name = "touch",
                parameters = emptyList(),
                returnType = EtsVoidType,
            )
        ).also {
            var index = 0
            val stmts = listOf(
                EtsAssignStmt(
                    location = EtsInstLocation(it, index++),
                    lhv = EtsLocal("this", EtsClassType(classBoxSignature)),
                    rhv = EtsThis(EtsClassType(classBoxSignature)),
                ),
                EtsCallStmt(
                    location = EtsInstLocation(it, index++),
                    expr = EtsInstanceCallExpr(
                        instance = EtsLocal("this", EtsClassType(classBoxSignature)),
                        method = methodMeow.signature,
                        args = emptyList(),
                    )
                ),
                EtsReturnStmt(
                    location = EtsInstLocation(it, index++),
                    returnValue = null,
                )
            )
            check(index == stmts.size)
            val successors = mapOf(
                stmts[0] to listOf(stmts[1]),
                stmts[1] to listOf(stmts[2]),
            )
            it._cfg = EtsCfg(
                stmts = stmts,
                successorMap = successors,
            )
        }
        val ctorBox = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classBoxSignature,
                name = "constructor",
                parameters = emptyList(),
                returnType = EtsVoidType,
            ),
        )
        val classBox = EtsClassImpl(
            signature = classBoxSignature,
            fields = listOf(fieldCats),
            methods = listOf(methodTouch),
            ctor = ctorBox,
        )
        val fileBox = EtsFile(
            signature = fileBoxSignature,
            classes = listOf(classBox),
            namespaces = emptyList(),
        )

        val project = EtsScene(files = listOf(fileCat, fileBox))
        val graph = EtsApplicationGraphImpl(project)

        val callStmt = project.classes
            .asSequence()
            .flatMap { it.methods }
            .filter { it.name == "touch" }
            .flatMap { it.cfg.stmts }
            .filterIsInstance<EtsCallStmt>()
            .first()
        assertEquals(methodMeow, graph.callees(callStmt).first())
    }
}
