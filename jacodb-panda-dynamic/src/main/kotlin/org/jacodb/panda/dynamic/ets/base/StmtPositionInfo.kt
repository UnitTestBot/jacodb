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

package org.jacodb.panda.dynamic.ets.base

/**
 * Information about the position of a statement.
 */
interface StmtPositionInfo {
    val position: Position

    fun getOperandPosition(index: Int): Position?

    companion object {
        val NO_POSITION_INFO = object : StmtPositionInfo {
            override val position = Position.NO_POSITION
            override fun getOperandPosition(index: Int): Position = Position.NO_POSITION
            override fun toString(): String = "NoPositionInformation"
        }
    }
}

/**
 * Simple statement position information that
 * only contains the position of the statement.
 */
data class SimpleStmtPositionInfo(
    override val position: Position,
) : StmtPositionInfo {
    override fun getOperandPosition(index: Int): Position? = null

    override fun toString(): String {
        return "stmt at: $position"
    }
}

/**
 * Full statement position information that
 * contains the position of the statement
 * and the positions of its operands.
 */
data class FullStmtPositionInfo(
    override val position: Position,
    val operandPositions: List<Position>,
) : StmtPositionInfo {
    override fun getOperandPosition(index: Int): Position = operandPositions[index]

    override fun toString(): String {
        return "stmt at: $position, operands at: $operandPositions"
    }
}
