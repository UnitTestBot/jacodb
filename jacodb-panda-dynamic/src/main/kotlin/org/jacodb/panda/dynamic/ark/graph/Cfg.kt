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

package org.jacodb.panda.dynamic.ark.graph

import org.jacodb.panda.dynamic.ark.base.Stmt

class Cfg {
    val blocks: MutableList<BasicBlock> = mutableListOf()
    val stmtToBlock: MutableMap<Int, BasicBlock> = hashMapOf()

    val startStmt: Stmt
        get() = blocks.first().stmts.first()

    fun successors(node: BasicBlock): List<BasicBlock> {
        TODO("Not yet implemented")
    }

    fun predecessors(node: BasicBlock): List<BasicBlock> {
        TODO("Not yet implemented")
    }
}
