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

package org.jacodb.taint.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jacodb.api.JcType

interface ConditionVisitor<out R> {
    fun visit(condition: ConstantTrue): R
    fun visit(condition: Not): R
    fun visit(condition: And): R
    fun visit(condition: Or): R
    fun visit(condition: IsConstant): R
    fun visit(condition: IsType): R
    fun visit(condition: AnnotationType): R
    fun visit(condition: ConstantEq): R
    fun visit(condition: ConstantLt): R
    fun visit(condition: ConstantGt): R
    fun visit(condition: ConstantMatches): R
    fun visit(condition: SourceFunctionMatches): R
    fun visit(condition: ContainsMark): R
    fun visit(condition: TypeMatches): R
}

interface Condition {
    fun <R> accept(conditionVisitor: ConditionVisitor<R>): R
}

val conditionModule = SerializersModule {
    polymorphic(Condition::class) {
        subclass(ConstantTrue::class)
        subclass(Not::class)
        subclass(And::class)
        subclass(Or::class)
        subclass(IsConstant::class)
        subclass(IsType::class)
        subclass(AnnotationType::class)
        subclass(ConstantEq::class)
        subclass(ConstantLt::class)
        subclass(ConstantGt::class)
        subclass(ConstantMatches::class)
        subclass(SourceFunctionMatches::class)
        subclass(ContainsMark::class)
        subclass(TypeMatches::class)
    }
}

/**
 * A constant true condition.
 */
@Serializable
@SerialName("ConstantTrue")
object ConstantTrue : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)

    override fun toString(): String = javaClass.simpleName
}

/**
 * A negation of the [arg].
 */
@Serializable
@SerialName("Not")
data class Not(
    @SerialName("condition") val arg: Condition,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

/**
 * A conjunction of the [args].
 */
@Serializable
@SerialName("And")
data class And(
    val args: List<Condition>,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

/**
 * A disjunction of the [args].
 */
@Serializable
@SerialName("Or")
data class Or(
    val args: List<Condition>,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

/**
 * A condition that an object at the [position] is a constant value,
 * not an environment variable or a method parameter.
 */
@Serializable
@SerialName("IsConstant")
data class IsConstant(
    val position: Position,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

/**
 * A condition that an object at the [position] matches with the [typeMatcher].
 */
@Serializable
@SerialName("IsType")
data class IsType(
    val position: Position,
    @SerialName("type") val typeMatcher: TypeMatcher,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

/**
 * A condition that an object at the [position] contains an annotation matching with the [typeMatcher].
 */
@Serializable
@SerialName("AnnotationType")
data class AnnotationType(
    val position: Position,
    @SerialName("type") val typeMatcher: TypeMatcher,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

/**
 * A condition that a value at the [position] is equal to a [value].
 */
@Serializable
@SerialName("ConstantEq")
data class ConstantEq(
    val position: Position,
    @SerialName("constant") val value: ConstantValue,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

/**
 * A condition that a value at the [position] is less than a [value].
 * The meaning of `less` is specific for each type of the [ConstantValue].
 */
@Serializable
@SerialName("ConstantLt")
data class ConstantLt(
    val position: Position,
    @SerialName("constant") val value: ConstantValue,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

/**
 * A condition that a value at the [position] is greater than a [value].
 * The meaning of `greater` is specific for each type of the [ConstantValue].
 */
@Serializable
@SerialName("ConstantGt")
data class ConstantGt(
    val position: Position,
    @SerialName("constant") val value: ConstantValue,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

/**
 * A condition that a value at the [position] is matching with the [pattern].
 */
@Serializable
@SerialName("ConstantMatches")
data class ConstantMatches(
    val position: Position,
    val pattern: String,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("SourceFunctionMatches")
data class SourceFunctionMatches(
    val position: Position,
    @SerialName("sourceFunction") val functionMatcher: FunctionMatcher,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

/**
 * A condition that a value at the [position] contains the [mark].
 */
@Serializable
@SerialName("ContainsMark")
data class ContainsMark(
    val position: Position,
    val mark: TaintMark,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

/**
 * A condition that a value at the [position] matches exactly with the [type].
 */
@Serializable
@SerialName("TypeMatches")
data class TypeMatches(
    val position: Position,
    val type: JcType,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
sealed interface ConstantValue

@Serializable
@SerialName("IntValue")
data class ConstantIntValue(val value: Int) : ConstantValue

@Serializable
@SerialName("BoolValue")
data class ConstantBooleanValue(val value: Boolean) : ConstantValue

@Serializable
@SerialName("StringValue")
data class ConstantStringValue(val value: String) : ConstantValue

@Serializable
sealed interface NameMatcher

@Serializable
@SerialName("NameIsEqualTo")
data class NameExactMatcher(
    val name: String,
) : NameMatcher

@Serializable
@SerialName("NameMatches")
data class NamePatternMatcher(
    val pattern: String,
) : NameMatcher

@Serializable
@SerialName("AnyNameMatches")
object AnyNameMatcher : NameMatcher {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
sealed interface TypeMatcher

@Serializable
@SerialName("PrimitiveNameMatches")
data class PrimitiveNameMatcher(val name: String) : TypeMatcher

@Serializable
@SerialName("ClassMatcher")
data class ClassMatcher(
    @SerialName("packageMatcher") val pkg: NameMatcher,
    val classNameMatcher: NameMatcher,
) : TypeMatcher

@Serializable
@SerialName("AnyTypeMatches")
object AnyTypeMatcher : TypeMatcher {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("FunctionMatches")
data class FunctionMatcher(
    val cls: ClassMatcher,
    val functionName: NameMatcher,
    val parametersMatchers: List<ParameterMatcher>,
    val returnTypeMatcher: TypeMatcher,
    val applyToOverrides: Boolean,
    val functionLabel: String?,
    val modifier: Int,
    val exclude: List<FunctionMatcher>,
)

@Serializable
@SerialName("ParameterMatches")
data class ParameterMatcher(
    val index: Int,
    val typeMatcher: TypeMatcher,
)
