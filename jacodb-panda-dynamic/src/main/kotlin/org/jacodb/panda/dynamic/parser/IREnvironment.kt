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

package org.jacodb.panda.dynamic.parser

import org.jacodb.panda.dynamic.api.PandaLocalVar

class IREnvironment private constructor() {

    private var varLiteralToLocalVar = mutableMapOf<String, PandaLocalVar>()

    private var catchBBIdToTryBlocks = mutableMapOf<Int, Set<Int>>()

    fun copy(): IREnvironment = IREnvironment().apply {
        this.varLiteralToLocalVar = this@IREnvironment.varLiteralToLocalVar.toMutableMap()
        this.catchBBIdToTryBlocks = this@IREnvironment.catchBBIdToTryBlocks.toMutableMap()
    }

    fun getLocalVar(literal: String): PandaLocalVar? {
        return varLiteralToLocalVar[literal]
    }

    fun setLocalVar(literal: String, lv: PandaLocalVar) {
        varLiteralToLocalVar[literal] = lv
    }

    fun getTryBlocks(catchBBId: Int): Set<Int>? {
        return catchBBIdToTryBlocks[catchBBId]
    }

    fun addTryBlockBBId(catchBBId: Int, tryBlockBBId: Int) {
        catchBBIdToTryBlocks[catchBBId] = catchBBIdToTryBlocks.getOrDefault(catchBBId, emptySet()) + tryBlockBBId
    }

    companion object {
        val emptyEnv
            get() = IREnvironment()
    }

    fun <T, P> MutableList<MutableMap<T, P>>.copy(): MutableList<MutableMap<T, P>> {
        return this.map { m -> m.toMutableMap() }.toMutableList()
    }

    fun <T, P, G> MutableMap<T, MutableMap<P, G>>.copy(): MutableMap<T, MutableMap<P, G>> {
        return this.mapValues { (_, value) -> value.toMutableMap() }.toMutableMap()
    }
}
