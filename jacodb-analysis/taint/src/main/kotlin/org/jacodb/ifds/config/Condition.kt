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

package org.jacodb.ifds.config

import org.jacodb.ifds.domain.AccessPath
import org.jacodb.ifds.domain.ElementAccessor
import org.jacodb.ifds.util.Maybe
import org.jacodb.ifds.util.onSome
import org.jacodb.ifds.domain.toPath
import org.jacodb.ifds.taint.Tainted
import org.jacodb.api.cfg.JcBool
import org.jacodb.api.cfg.JcConstant
import org.jacodb.api.cfg.JcInt
import org.jacodb.api.cfg.JcStringConstant
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.isAssignable
import org.jacodb.taint.configuration.And
import org.jacodb.taint.configuration.AnnotationType
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

class FactAwareConditionEvaluator(
    private val fact: Tainted,
    positionResolver: PositionResolver<Maybe<JcValue>>,
) : BasicConditionEvaluator(positionResolver) {

    override fun visit(condition: ContainsMark): Boolean {
        if (fact.mark != condition.mark) return false
        positionResolver.resolve(condition.position).onSome { value ->
            val variable = value.toPath()

            // FIXME: Adhoc for arrays
            val variableWithoutStars = variable.removeTrailingElementAccessors()
            val factWithoutStars = fact.variable.removeTrailingElementAccessors()
            if (variableWithoutStars == factWithoutStars) return true

            return variable == fact.variable
        }
        return false
    }

    private fun AccessPath.removeTrailingElementAccessors(): AccessPath {
        val accesses = accesses.toMutableList()
        while (accesses.lastOrNull() is ElementAccessor) {
            accesses.removeLast()
        }
        return AccessPath(value, accesses)
    }
}
