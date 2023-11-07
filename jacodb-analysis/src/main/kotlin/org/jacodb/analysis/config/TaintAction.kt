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

package org.jacodb.analysis.config

import org.jacodb.analysis.engine.Tainted
import org.jacodb.analysis.paths.AccessPath
import org.jacodb.analysis.paths.startsWith
import org.jacodb.taint.configuration.Action
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.CopyAllMarks
import org.jacodb.taint.configuration.CopyMark
import org.jacodb.taint.configuration.PositionResolver
import org.jacodb.taint.configuration.RemoveAllMarks
import org.jacodb.taint.configuration.RemoveMark
import org.jacodb.taint.configuration.TaintActionVisitor

class TaintActionEvaluator(
    internal val positionResolver: PositionResolver<AccessPath>,
) {
    fun evaluate(action: CopyAllMarks, fact: Tainted): Tainted? {
        val from = positionResolver.resolve(action.from)
        if (from.startsWith(fact.variable)) {
            val to = positionResolver.resolve(action.to)
            return fact.copy(variable = to)
        }
        return null
    }

    fun evaluate(action: CopyMark, fact: Tainted): Tainted? {
        if (fact.mark == action.mark) {
            val from = positionResolver.resolve(action.from)
            if (from.startsWith(fact.variable)) {
                val to = positionResolver.resolve(action.to)
                return fact.copy(variable = to)
            }
        }
        return null
    }

    fun evaluate(action: AssignMark): Tainted {
        val variable = positionResolver.resolve(action.position)
        return Tainted(variable, action.mark)
    }

    fun evaluate(action: RemoveAllMarks, fact: Tainted): Tainted? {
        val variable = positionResolver.resolve(action.position)
        if (variable.startsWith(fact.variable)) {
            // TODO: maybe cut the path such that only the tail (until 'fact') is un-tainted?
            return null
        }
        return fact
    }

    fun evaluate(action: RemoveMark, fact: Tainted): Tainted? {
        if (fact.mark == action.mark) {
            val variable = positionResolver.resolve(action.position)
            if (variable.startsWith(fact.variable)) {
                // TODO: maybe cut the path such that only the tail (until 'fact') is un-tainted?
                return null
            }
        }
        return fact
    }
}

class FactAwareTaintActionEvaluator(
    private val fact: Tainted,
    private val evaluator: TaintActionEvaluator,
) : TaintActionVisitor<Tainted?> {

    constructor(
        fact: Tainted,
        positionResolver: PositionResolver<AccessPath>,
    ) : this(fact, TaintActionEvaluator(positionResolver))

    override fun visit(action: CopyAllMarks): Tainted? {
        return evaluator.evaluate(action, fact)
    }

    override fun visit(action: CopyMark): Tainted? {
        return evaluator.evaluate(action, fact)
    }

    override fun visit(action: AssignMark): Tainted {
        // Note: 'this.fact' is ignored
        return evaluator.evaluate(action)
    }

    override fun visit(action: RemoveAllMarks): Tainted? {
        return evaluator.evaluate(action, fact)
    }

    override fun visit(action: RemoveMark): Tainted? {
        return evaluator.evaluate(action, fact)
    }

    override fun visit(action: Action): Tainted? {
        error("$this cannot handle $action")
    }
}
