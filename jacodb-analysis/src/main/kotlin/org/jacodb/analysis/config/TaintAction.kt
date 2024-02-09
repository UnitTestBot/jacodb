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

import org.jacodb.analysis.ifds2.taint.Tainted
import org.jacodb.analysis.paths.AccessPath
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
    fun evaluate(action: CopyAllMarks, fact: Tainted): Collection<Tainted> {
        val from = positionResolver.resolve(action.from)
        if (from == fact.variable) {
            val to = positionResolver.resolve(action.to)
            return setOf(fact, fact.copy(variable = to))
        }
        return setOf(fact) // TODO: empty of singleton?
        // return emptySet()
    }

    fun evaluate(action: CopyMark, fact: Tainted): Collection<Tainted> {
        if (fact.mark == action.mark) {
            val from = positionResolver.resolve(action.from)
            if (from == fact.variable) {
                val to = positionResolver.resolve(action.to)
                return setOf(fact, fact.copy(variable = to))
            }
        }
        return setOf(fact) // TODO: empty or singleton?
        // return emptySet()
    }

    fun evaluate(action: AssignMark): Tainted {
        val variable = positionResolver.resolve(action.position)
        return Tainted(variable, action.mark)
    }

    fun evaluate(action: RemoveAllMarks, fact: Tainted): Collection<Tainted> {
        val variable = positionResolver.resolve(action.position)
        if (variable == fact.variable) {
            return emptySet()
        }
        return setOf(fact)
    }

    fun evaluate(action: RemoveMark, fact: Tainted): Collection<Tainted> {
        if (fact.mark == action.mark) {
            val variable = positionResolver.resolve(action.position)
            if (variable == fact.variable) {
                return emptySet()
            }
        }
        return setOf(fact)
    }
}

class FactAwareTaintActionEvaluator(
    private val fact: Tainted,
    private val evaluator: TaintActionEvaluator,
) : TaintActionVisitor<Collection<Tainted>> {

    constructor(
        fact: Tainted,
        positionResolver: PositionResolver<AccessPath>,
    ) : this(fact, TaintActionEvaluator(positionResolver))

    override fun visit(action: CopyAllMarks): Collection<Tainted> {
        return evaluator.evaluate(action, fact)
    }

    override fun visit(action: CopyMark): Collection<Tainted> {
        return evaluator.evaluate(action, fact)
    }

    override fun visit(action: AssignMark): Collection<Tainted> {
        return setOf(fact, evaluator.evaluate(action))
    }

    override fun visit(action: RemoveAllMarks): Collection<Tainted> {
        return evaluator.evaluate(action, fact)
    }

    override fun visit(action: RemoveMark): Collection<Tainted> {
        return evaluator.evaluate(action, fact)
    }

    override fun visit(action: Action): Collection<Tainted> {
        error("$this cannot handle $action")
    }
}
