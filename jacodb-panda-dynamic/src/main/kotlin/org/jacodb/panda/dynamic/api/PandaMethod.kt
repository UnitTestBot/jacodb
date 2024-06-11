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

class PandaMethod(
    override val name: String,
) : CommonMethod {

    lateinit var project: PandaProject
        internal set

    // TODO: replace with lateinit var
    @Suppress("PropertyName")
    internal var enclosingClass_: PandaClass? = null
    val enclosingClass: PandaClass
        get() = enclosingClass_ ?: error("Enclosing class not set")
    // TODO
    // override val enclosingClass: PandaClass
    //     get() = project.classes.find { it.name == className } ?: project.getGlobalClass()

    var blocks: List<PandaBasicBlock> = emptyList()
        internal set(value) {
            field = value
            flowGraph = null
        }
    var instructions: List<PandaInst> = emptyList()
        internal set(value) {
            field = value
            flowGraph = null
        }
    var parameterInfos: List<PandaParameterInfo> = emptyList()
        internal set
    var className: String? = null
        internal set

    var localVarsCount: Int = 0
        internal set

    override val parameters: List<PandaMethodParameter>
        get() = parameterInfos.map {
            PandaMethodParameter(
                it.index,
                "arg${it.index}",
                PandaNamedType(it.type.typeName),
            )
        }

    override val returnType: PandaType
        get() = PandaAnyType

    private var flowGraph: PandaGraph? = null

    override fun flowGraph(): PandaGraph {
        if (flowGraph == null) {
            flowGraph = PandaBlockGraph(blocks, instructions).graph
        }
        return flowGraph!!
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PandaMethod

        if (name != other.name) return false
        if (parameterInfos != other.parameterInfos) return false
        // TODO waiting for support
        // if (enclosingClass != other.enclosingClass) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + parameterInfos.hashCode()
        // TODO waiting for support
        // result = 31 * result + enclosingClass.hashCode()
        return result
    }

    override fun toString(): String {
        val params = parameters.joinToString()
        return "$name($params): $returnType"
    }
}

class PandaParameterInfo(
    val index: Int,
    val type: PandaType,
)

data class PandaMethodParameter(
    val index: Int,
    val name: String?,
    override val type: PandaType,
) : CommonMethodParameter {
    override fun toString(): String {
        return "${name ?: "arg$index"}: ${type.typeName}"
    }
}
