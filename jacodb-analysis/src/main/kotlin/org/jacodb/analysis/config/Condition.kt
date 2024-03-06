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

import org.jacodb.analysis.ifds.Maybe
import org.jacodb.analysis.ifds.onSome
import org.jacodb.analysis.ifds.toPath
import org.jacodb.analysis.taint.Tainted
import org.jacodb.analysis.util.removeTrailingElementAccessors
import org.jacodb.api.cfg.JcBool
import org.jacodb.api.cfg.JcConstant
import org.jacodb.api.cfg.JcInt
import org.jacodb.api.cfg.JcStringConstant
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.isAssignable
import org.jacodb.taint.configuration.And
import org.jacodb.taint.configuration.AnnotationType
import org.jacodb.taint.configuration.Condition
import org.jacodb.taint.configuration.ConditionVisitor
import org.jacodb.taint.configuration.ConstantBooleanValue
import org.jacodb.taint.configuration.ConstantEq
import org.jacodb.taint.configuration.ConstantGt
import org.jacodb.taint.configuration.ConstantIntValue
import org.jacodb.taint.configuration.ConstantLt
import org.jacodb.taint.configuration.ConstantMatches
import org.jacodb.taint.configuration.ConstantStringValue
import org.jacodb.taint.configuration.ConstantTrue
import org.jacodb.taint.configuration.ContainsMark
import org.jacodb.taint.configuration.IsConstant
import org.jacodb.taint.configuration.IsType
import org.jacodb.taint.configuration.Not
import org.jacodb.taint.configuration.Or
import org.jacodb.taint.configuration.PositionResolver
import org.jacodb.taint.configuration.SourceFunctionMatches
import org.jacodb.taint.configuration.TypeMatches

open class BasicConditionEvaluator(
    internal val positionResolver: PositionResolver<Maybe<JcValue>>,
) : ConditionVisitor<Boolean> {

    override fun visit(condition: ConstantTrue): Boolean {
        return true
    }

    override fun visit(condition: Not): Boolean {
        return !condition.arg.accept(this)
    }

    override fun visit(condition: And): Boolean {
        return condition.args.all { it.accept(this) }
    }

    override fun visit(condition: Or): Boolean {
        return condition.args.any { it.accept(this) }
    }

    override fun visit(condition: IsConstant): Boolean {
        positionResolver.resolve(condition.position).onSome { return it is JcConstant }
        return false
    }

    override fun visit(condition: IsType): Boolean {
        // Note: TaintConfigurationFeature.ConditionSpecializer is responsible for
        // expanding IsType condition upon parsing the taint configuration.
        error("Unexpected condition: $condition")
    }

    override fun visit(condition: AnnotationType): Boolean {
        // Note: TaintConfigurationFeature.ConditionSpecializer is responsible for
        // expanding AnnotationType condition upon parsing the taint configuration.
        error("Unexpected condition: $condition")
    }

    override fun visit(condition: ConstantEq): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            return when (val constant = condition.value) {
                is ConstantBooleanValue -> {
                    value is JcBool && value.value == constant.value
                }

                is ConstantIntValue -> {
                    value is JcInt && value.value == constant.value
                }

                is ConstantStringValue -> {
                    // TODO: if 'value' is not string, convert it to string and compare with 'constant.value'
                    value is JcStringConstant && value.value == constant.value
                }
            }
        }
        return false
    }

    override fun visit(condition: ConstantLt): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            return when (val constant = condition.value) {
                is ConstantIntValue -> {
                    value is JcInt && value.value < constant.value
                }

                else -> error("Unexpected constant: $constant")
            }
        }
        return false
    }

    override fun visit(condition: ConstantGt): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            return when (val constant = condition.value) {
                is ConstantIntValue -> {
                    value is JcInt && value.value > constant.value
                }

                else -> error("Unexpected constant: $constant")
            }
        }
        return false
    }

    override fun visit(condition: ConstantMatches): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            val re = condition.pattern.toRegex()
            return re.matches(value.toString())
        }
        return false
    }

    override fun visit(condition: SourceFunctionMatches): Boolean {
        TODO("Not implemented yet")
    }

    override fun visit(condition: ContainsMark): Boolean {
        error("This visitor does not support condition $condition. Use FactAwareConditionEvaluator instead")
    }

    override fun visit(condition: TypeMatches): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            return value.type.isAssignable(condition.type)
        }
        return false
    }
}

class IgnorantConditionEvaluator(
    positionResolver: PositionResolver<Maybe<JcValue>>,
) : BasicConditionEvaluator(positionResolver) {

    override fun visit(condition: ContainsMark): Boolean {
        return false
    }
}

class FactAwareConditionEvaluator(
    private val fact: Tainted,
    positionResolver: PositionResolver<Maybe<JcValue>>,
) : BasicConditionEvaluator(positionResolver) {

    override fun visit(condition: ContainsMark): Boolean {
        if (fact.mark != condition.mark) return false
        positionResolver.resolve(condition.position).onSome { value ->
            val variable = value.toPath()

            // FIXME: adhoc for arrays
            if (variable.removeTrailingElementAccessors() == fact.variable.removeTrailingElementAccessors()) return true

            return variable == fact.variable
        }
        return false
    }
}

class AdhocFactAwareConditionEvaluator(
    private val fact: Tainted,
    private val positionResolver: PositionResolver<Maybe<JcValue>>,
) : ConditionVisitor<List<Assumed>> {

    private var hasEvaluatedContainsMark: Boolean = false

    fun apply(condition: Condition): List<Assumed> = try {
        hasEvaluatedContainsMark = false
        val result = condition.toNnf(negated = false).accept(this).filter { it.result }
        if (hasEvaluatedContainsMark) result else emptyList()
    } finally {
        hasEvaluatedContainsMark = false
    }

    override fun visit(condition: ConstantTrue): List<Assumed> {
        return true.withoutAssumptions
    }

    override fun visit(condition: Not): List<Assumed> {
        return condition.arg.accept(this).mapNotNull {
            if (it.result) return@mapNotNull null

            it.copy(result = true)
        }
    }

    override fun visit(condition: And): List<Assumed> {
        val product = condition.args.map { it.accept(this).filter { it.result } }.cartesianProduct()
        return product.mapTo(mutableListOf()) { results ->
            var mergedAssumptions: Set<Tainted>? = null
            var mergedMutableAssumptions: MutableSet<Tainted>? = null

            for (r in results) {
                if (r.assumptions.isEmpty()) continue

                if (mergedAssumptions == null) {
                    mergedAssumptions = r.assumptions
                    continue
                }

                if (mergedMutableAssumptions == null) {
                    mergedMutableAssumptions = mergedAssumptions.toMutableSet()
                    mergedAssumptions = mergedMutableAssumptions
                }

                mergedMutableAssumptions.addAll(r.assumptions)
            }

            Assumed(true, mergedAssumptions ?: emptySet())
        }
    }

    override fun visit(condition: Or): List<Assumed> {
        return condition.args.flatMap { it.accept(this).filter { it.result } }
    }

    override fun visit(condition: IsConstant): List<Assumed> {
        positionResolver.resolve(condition.position).onSome {
            return (it is JcConstant).withoutAssumptions
        }
        return false.withoutAssumptions
    }

    override fun visit(condition: IsType): List<Assumed> {
        // Note: TaintConfigurationFeature.ConditionSpecializer is responsible for
        // expanding IsType condition upon parsing the taint configuration.
        error("Unexpected condition: $condition")
    }

    override fun visit(condition: AnnotationType): List<Assumed> {
        // Note: TaintConfigurationFeature.ConditionSpecializer is responsible for
        // expanding AnnotationType condition upon parsing the taint configuration.
        error("Unexpected condition: $condition")
    }

    override fun visit(condition: ConstantEq): List<Assumed> {
        positionResolver.resolve(condition.position).onSome { value ->
            return when (val constant = condition.value) {
                is ConstantBooleanValue -> {
                    value is JcBool && value.value == constant.value
                }

                is ConstantIntValue -> {
                    value is JcInt && value.value == constant.value
                }

                is ConstantStringValue -> {
                    // TODO: if 'value' is not string, convert it to string and compare with 'constant.value'
                    value is JcStringConstant && value.value == constant.value
                }
            }.withoutAssumptions
        }
        return false.withoutAssumptions
    }

    override fun visit(condition: ConstantLt): List<Assumed> {
        positionResolver.resolve(condition.position).onSome { value ->
            return when (val constant = condition.value) {
                is ConstantIntValue -> {
                    value is JcInt && value.value < constant.value
                }

                else -> error("Unexpected constant: $constant")
            }.withoutAssumptions
        }
        return false.withoutAssumptions
    }

    override fun visit(condition: ConstantGt): List<Assumed> {
        positionResolver.resolve(condition.position).onSome { value ->
            return when (val constant = condition.value) {
                is ConstantIntValue -> {
                    value is JcInt && value.value > constant.value
                }

                else -> error("Unexpected constant: $constant")
            }.withoutAssumptions
        }
        return false.withoutAssumptions
    }

    override fun visit(condition: ConstantMatches): List<Assumed> {
        positionResolver.resolve(condition.position).onSome { value ->
            val re = condition.pattern.toRegex()
            return re.matches(value.toString()).withoutAssumptions
        }
        return false.withoutAssumptions
    }

    override fun visit(condition: SourceFunctionMatches): List<Assumed> {
        TODO("Not implemented yet")
    }

    override fun visit(condition: ContainsMark): List<Assumed> {
        if (fact.mark == condition.mark) {
            positionResolver.resolve(condition.position).onSome { value ->
                val variable = value.toPath()

                // FIXME: Adhoc for arrays
                val variableWithoutStars = variable.removeTrailingElementAccessors()
                val factWithoutStars = fact.variable.removeTrailingElementAccessors()
                if (variableWithoutStars == factWithoutStars) {
                    hasEvaluatedContainsMark = true
                    return true.withoutAssumptions
                }

                if (variable == fact.variable) {
                    hasEvaluatedContainsMark = true
                    return true.withoutAssumptions
                }
            }
            return false.withoutAssumptions
        } else {
            val results = mutableListOf<Assumed>()
            results += false.withoutAssumptions

            positionResolver.resolve(condition.position).onSome { value ->
                val variable = value.toPath()
                results += Assumed(true, setOf(Tainted(variable, condition.mark)))
            }

            return results
        }
    }

    override fun visit(condition: TypeMatches): List<Assumed> {
        positionResolver.resolve(condition.position).onSome { value ->
            return value.type.isAssignable(condition.type).withoutAssumptions
        }
        return false.withoutAssumptions
    }
}

data class Assumed(
    val result: Boolean,
    val assumptions: Set<Tainted>,
)

private val trueWithoutAssumptions = listOf(Assumed(result = true, assumptions = emptySet()))
private val falseWithoutAssumptions = listOf(Assumed(result = false, assumptions = emptySet()))

private val Boolean.withoutAssumptions: List<Assumed>
    get() = if (this) trueWithoutAssumptions else falseWithoutAssumptions

fun Condition.toNnf(negated: Boolean): Condition = when (this) {
    is Not -> arg.toNnf(!negated)

    is And -> if (!negated) {
        And(args.map { it.toNnf(negated = false) })
    } else {
        Or(args.map { it.toNnf(negated = true) })
    }

    is Or -> if (!negated) {
        Or(args.map { it.toNnf(negated = false) })
    } else {
        And(args.map { it.toNnf(negated = true) })
    }

    else -> if (negated) Not(this) else this
}

/**
 * Returns the cartesian product of iterables.
 * Resulting tuples (as `List<T>`) are emitted lazily in lexicographic sort order.
 *
 * Example:
 *  - `xs = [ {1,2,3,4}, "ABC", {"cat","dog"} ]`
 *  - (1) `acc = [ [] ]`, `iterable = {1,2,3,4}`
 *     - `acc.map = [ [[]+1], [[]+2], [[]+3], [[]+4] ]`
 *     - `flatMap = [ [1], [2], [3], [4] ]`
 *  - (2) `acc = [ [1], [2], [3], [4] ]`, `iterable = "ABC"`
 *     - `acc.map = [ [[1]+A, [1]+B, [1]+C], [[2]+A, [2]+B, [2]+C], ... ]`
 *     - `flatMap = [ [1,A], [1,B], [1,C], [2,A], [2,B], [2,C], ... ]`
 *  - (3) `acc = [ [1,A], [1,B], [1,C], [2,A], [2,B], [2,C], ... ]`, `iterable = {"cat","dog"}`
 *     - `acc.map = [ [[1,A]+cat, [1,A]+dog], [[1,B]+cat, [1,B]+dog], ... ]`
 *     - `flatMap = [ [1,A,cat], [1,A,dog], [1,B,cat], [1,B,dog], ... ]`
 */
fun <T> Iterable<Iterable<T>>.cartesianProduct(): Sequence<List<T>> =
    fold(sequenceOf(listOf())) { acc, iterable ->
        acc.flatMap { list ->
            iterable.asSequence().map { element -> list + element }
        }
    }
