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

import org.jacodb.panda.dynamic.api.*

class IREnvironment private constructor() {

    private var varLiteralToLocalVar = mutableMapOf<String, PandaLocalVar>()

    private var catchBBIdToTryBlockBBId = mutableMapOf<Int, Int>()

    private var lexenvToLexvarStorage = mutableListOf<MutableMap<Int, Pair<String, PandaValue>>>()

    private var localToAssignment = mutableMapOf<String, MutableMap<Int, PandaAssignInst>>()

    fun copy(): IREnvironment = IREnvironment().apply {
        this.varLiteralToLocalVar = this@IREnvironment.varLiteralToLocalVar
        this.catchBBIdToTryBlockBBId = this@IREnvironment.catchBBIdToTryBlockBBId
        this.lexenvToLexvarStorage = this@IREnvironment.lexenvToLexvarStorage
        this.localToAssignment = this@IREnvironment.localToAssignment
    }

    fun getLocalVar(literal: String): PandaLocalVar? {
        return varLiteralToLocalVar[literal]
    }

    fun setLocalVar(literal: String, lv: PandaLocalVar) {
        varLiteralToLocalVar[literal] = lv
    }

    fun getTryBlockBBId(catchBBId: Int): Int? {
        return catchBBIdToTryBlockBBId[catchBBId]
    }

    fun setTryBlockBBId(catchBBId: Int, tryBlockBBId: Int) {
        catchBBIdToTryBlockBBId[catchBBId] = tryBlockBBId
    }

    fun getLexvar(lexenv: Int, lexvar: Int): Pair<String, PandaValue> {
        return lexenvToLexvarStorage[lexenv][lexvar]
            ?: error("lexvar not set")
    }

    fun setLexvar(lexenv: Int, lexvar: Int, methodName: String, value: PandaValue) {
        lexenvToLexvarStorage[lexenv][lexvar] = Pair(methodName, value)
    }

    fun newLexenv() {
        lexenvToLexvarStorage.add(0, mutableMapOf())
    }

    fun popLexenv() {
        lexenvToLexvarStorage.removeAt(0)
    }

    fun setLocalAssignment(methodName: String, lv: PandaLocalVar, assn: PandaAssignInst) {
        val methodLocals = localToAssignment.getOrPut(methodName, ::mutableMapOf)
        methodLocals[lv.index] = assn
    }

    fun getLocalAssignment(methodName: String, lv: PandaLocalVar): PandaAssignInst {
        val methodLocals = localToAssignment.getOrDefault(methodName, mutableMapOf())
        return methodLocals[lv.index]
            ?: error("No assignment")
    }

    companion object {
        val emptyEnv = IREnvironment()
    }

}


