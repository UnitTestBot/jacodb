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

import org.jacodb.api.core.CoreMethod
import org.jacodb.api.core.cfg.ControlFlowGraph

class PandaMethod(
    val name: String,
    val returnType: PandaType
) : CoreMethod<PandaInst> {

    private var _blocks: List<PandaBasicBlock> = emptyList()
    private var _instructions: List<PandaInst> = emptyList()
    private var _parameters: List<PandaParameterInfo> = emptyList()
    private var _className: String? = null
    private var _project: PandaProject = PandaProject.empty()

    val className: String? get() = _className
    val project: PandaProject get() = _project
    val instructions: List<PandaInst> get() = _instructions
    val parameters: List<PandaParameterInfo> get() = _parameters

    fun setProject(value: PandaProject) {
        _project = value
    }

    fun initBlocks(blocks: List<PandaBasicBlock>) {
        this._blocks = blocks
    }

    fun initInstructions(instructions: List<PandaInst>) {
        this._instructions = instructions
    }

    fun initParameters(parameters: List<PandaParameterInfo>) {
        this._parameters = parameters
    }

    fun initClassName(className: String) {
        this._className = className
    }

    val signature: String
        get() = parameters.joinToString(separator = ", ") {
            "arg ${it.index}: ${it.type.typeName}"
        }

    override fun flowGraph(): ControlFlowGraph<PandaInst> {
        return PandaBlockGraph(_blocks, instructions).graph
    }

    override fun toString() = "function $name($signature): $returnType"
}

class PandaParameterInfo(
    val index: Int,
    val type: PandaType
)
