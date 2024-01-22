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

import org.jacodb.analysis.ifds2.Tainted
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPath
import org.jacodb.api.JcClassType
import org.jacodb.api.cfg.JcBool
import org.jacodb.api.cfg.JcConstant
import org.jacodb.api.cfg.JcInt
import org.jacodb.api.cfg.JcStringConstant
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.isSubClassOf
import org.jacodb.taint.configuration.And
import org.jacodb.taint.configuration.AnnotationType
import org.jacodb.taint.configuration.ContainsMark
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
import org.jacodb.taint.configuration.IsConstant
import org.jacodb.taint.configuration.IsType
import org.jacodb.taint.configuration.Not
import org.jacodb.taint.configuration.Or
import org.jacodb.taint.configuration.PositionResolver
import org.jacodb.taint.configuration.SourceFunctionMatches
import org.jacodb.taint.configuration.TypeMatches

abstract class DefaultConditionVisitor<out R>(
    private val defaultConditionHandler: (Condition) -> R,
) : ConditionVisitor<R> {
    final override fun visit(condition: Condition): R = defaultConditionHandler(condition)

    override fun visit(condition: And): R = defaultConditionHandler(condition)
    override fun visit(condition: Or): R = defaultConditionHandler(condition)
    override fun visit(condition: Not): R = defaultConditionHandler(condition)
    override fun visit(condition: IsConstant): R = defaultConditionHandler(condition)
    override fun visit(condition: IsType): R = defaultConditionHandler(condition)
    override fun visit(condition: AnnotationType): R = defaultConditionHandler(condition)
    override fun visit(condition: ConstantEq): R = defaultConditionHandler(condition)
    override fun visit(condition: ConstantLt): R = defaultConditionHandler(condition)
    override fun visit(condition: ConstantGt): R = defaultConditionHandler(condition)
    override fun visit(condition: ConstantMatches): R = defaultConditionHandler(condition)
    override fun visit(condition: SourceFunctionMatches): R = defaultConditionHandler(condition)
    override fun visit(condition: ContainsMark): R = defaultConditionHandler(condition)
    override fun visit(condition: ConstantTrue): R = defaultConditionHandler(condition)
    override fun visit(condition: TypeMatches): R = defaultConditionHandler(condition)
}

abstract class DefaultConditionEvaluator(
    defaultConditionHandler: (Condition) -> Boolean = { false },
) : DefaultConditionVisitor<Boolean>(defaultConditionHandler) {

    final override fun visit(condition: And): Boolean {
        return condition.args.all { it.accept(this) }
    }

    final override fun visit(condition: Or): Boolean {
        return condition.args.any { it.accept(this) }
    }

    final override fun visit(condition: Not): Boolean {
        return !condition.arg.accept(this)
    }

    final override fun visit(condition: ConstantTrue): Boolean {
        return true
    }
}

class BasicConditionEvaluator(
    internal val positionResolver: PositionResolver<JcValue>,
) : DefaultConditionEvaluator() {
    override fun visit(condition: IsConstant): Boolean {
        val value = positionResolver.resolve(condition.position)
        return value is JcConstant
    }

    override fun visit(condition: IsType): Boolean {
        error("Unexpected condition: $condition")
    }

    override fun visit(condition: AnnotationType): Boolean {
        // TODO("Not implemented yet")
        error("Unexpected condition: $condition")
    }

    override fun visit(condition: ConstantEq): Boolean {
        val value = positionResolver.resolve(condition.position)
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

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun visit(condition: ConstantLt): Boolean {
        val value = positionResolver.resolve(condition.position)
        return when (val constant = condition.value) {
            is ConstantIntValue -> {
                value is JcInt && value.value < constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun visit(condition: ConstantGt): Boolean {
        val value = positionResolver.resolve(condition.position)
        return when (val constant = condition.value) {
            is ConstantIntValue -> {
                value is JcInt && value.value > constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun visit(condition: ConstantMatches): Boolean {
        val value = positionResolver.resolve(condition.position)
        val re = condition.pattern.toRegex()
        return re.matches(value.toString())
    }

    override fun visit(condition: SourceFunctionMatches): Boolean {
        TODO("Not implemented yet")
    }

    override fun visit(condition: ContainsMark): Boolean {
        error("This visitor does not support condition $condition. Use FactAwareConditionEvaluator instead")
    }

    override fun visit(condition: TypeMatches): Boolean {
        val value = positionResolver.resolve(condition.position)
        // return value.type == condition.type
        return when (val valueType = value.type) {
            is JcClassType -> {
                when (val conditionType = condition.type) {
                    is JcClassType -> valueType.jcClass isSubClassOf conditionType.jcClass
                    // TODO: handle primitive types also. See 'JcTypes.kt' for reference impl.
                    else -> false
                }
            }

            else -> false
        }
    }
}

class FactAwareConditionEvaluator(
    private val fact: Tainted,
    private val basicConditionEvaluator: BasicConditionEvaluator,
) : DefaultConditionEvaluator() {

    constructor(
        fact: Tainted,
        positionResolver: PositionResolver<JcValue>,
    ) : this(fact, BasicConditionEvaluator(positionResolver))

    override fun visit(condition: ContainsMark): Boolean {
        if (fact.mark == condition.mark) {
            val value = basicConditionEvaluator.positionResolver.resolve(condition.position)
            val variable = value.toPath()
            if (variable.startsWith(fact.variable)) {
                return true
            }
        }
        return false
    }

    // Delegates

    override fun visit(condition: IsConstant): Boolean {
        return basicConditionEvaluator.visit(condition)
    }

    override fun visit(condition: IsType): Boolean {
        return basicConditionEvaluator.visit(condition)
    }

    override fun visit(condition: AnnotationType): Boolean {
        return basicConditionEvaluator.visit(condition)
    }

    override fun visit(condition: ConstantEq): Boolean {
        return basicConditionEvaluator.visit(condition)
    }

    override fun visit(condition: ConstantLt): Boolean {
        return basicConditionEvaluator.visit(condition)
    }

    override fun visit(condition: ConstantGt): Boolean {
        return basicConditionEvaluator.visit(condition)
    }

    override fun visit(condition: ConstantMatches): Boolean {
        return basicConditionEvaluator.visit(condition)
    }

    override fun visit(condition: SourceFunctionMatches): Boolean {
        return basicConditionEvaluator.visit(condition)
    }

    override fun visit(condition: TypeMatches): Boolean {
        return basicConditionEvaluator.visit(condition)
    }
}
