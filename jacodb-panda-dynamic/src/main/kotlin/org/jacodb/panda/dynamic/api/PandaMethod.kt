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

package org.jacodb.panda.dynamic.api

import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.CommonMethodParameter

open class PandaMethod(
    override val name: String,
    override val enclosingClass: PandaClass,
    override val returnType: PandaTypeName,
) : CommonMethod<PandaMethod, PandaInst> {

    var project: PandaProject = PandaProject.empty()
        internal set
    var blocks: List<PandaBasicBlock> = emptyList()
        internal set
    var instructions: List<PandaInst> = emptyList()
        internal set
    var parameterInfos: List<PandaParameterInfo> = emptyList()
        internal set
    var className: String? = null
        internal set

    override val parameters: List<PandaMethodParameter>
        get() = parameterInfos.map { TODO() }

    private val blockGraph = PandaBlockGraph(blocks, instructions)
    override fun flowGraph(): PandaGraph {
        return blockGraph.graph
    }

    private val signature: String
        get() = parameterInfos.joinToString(separator = ", ") {
            "arg ${it.index}: ${it.type.typeName}"
        }

    override fun toString() = "function $name($signature): $returnType"
}

class PandaStdMethod(
    name: String,
    enclosingClass: PandaClass,
    returnType: PandaType,
) : PandaMethod(name, enclosingClass, returnType)

class PandaParameterInfo(
    val index: Int,
    val type: PandaType,
)

class PandaMethodParameter(
    override val type: PandaTypeName,
    override val name: String?,
    override val index: Int,
    override val method: PandaMethod,
) : CommonMethodParameter
