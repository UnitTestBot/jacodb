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

package org.jacodb.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jacodb.api.JcType

interface ConditionVisitor<R> {
    fun visit(condition: And): R
    fun visit(condition: Or): R
    fun visit(condition: Not): R
    fun visit(condition: IsConstant): R
    fun visit(condition: IsType): R
    fun visit(condition: AnnotationType): R
    fun visit(condition: ConstantEq): R
    fun visit(condition: ConstantLt): R
    fun visit(condition: ConstantGt): R
    fun visit(condition: ConstantMatches): R
    fun visit(condition: SourceFunctionMatches): R
    fun visit(condition: CallParameterContainsMark): R
    fun visit(condition: ConstantTrue): R

    // extra conditions
    fun visit(condition: TypeMatches): R

    // external type
    fun visit(condition: Condition): R
}

interface Condition {
    fun <R> accept(conditionVisitor: ConditionVisitor<R>): R
}

@Serializable
@SerialName("And")
data class And(
    @SerialName("args") val args: List<Condition>,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("Or")
data class Or(
    @SerialName("args") val args: List<Condition>,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("Not")
data class Not(
    @SerialName("condition") val arg: Condition,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("IsConstant")
data class IsConstant(
    @SerialName("position") val position: Position,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("IsType")
data class IsType(
    @SerialName("position") val position: Position,
    @SerialName("type") val typeMatcher: TypeMatcher,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("AnnotationType")
data class AnnotationType(
    @SerialName("position") val position: Position,
    @SerialName("type") val typeMatcher: TypeMatcher,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("ConstantEq")
data class ConstantEq(
    @SerialName("position") val position: Position,
    @SerialName("constant") val value: ConstantValue,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("ConstantLt")
data class ConstantLt(
    @SerialName("position") val position: Position,
    @SerialName("constant") val value: ConstantValue,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("ConstantGt")
data class ConstantGt(
    @SerialName("position") val position: Position,
    @SerialName("constant") val value: ConstantValue,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("ConstantMatches")
data class ConstantMatches(
    @SerialName("position") val position: Position,
    @SerialName("pattern") val pattern: String,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("SourceFunctionMatches")
data class SourceFunctionMatches(
    @SerialName("position") val position: Position,
    @SerialName("sourceFunction") val functionMatcher: FunctionMatcher,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

// sink label
@Serializable
@SerialName("ContainsMark")
data class CallParameterContainsMark(
    @SerialName("position") val position: Position,
    @SerialName("mark") val mark: TaintMark,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("ConstantTrue")
object ConstantTrue : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

// Extra conditions
@Serializable
@SerialName("TypeMatches")
data class TypeMatches(
    @SerialName("position") val position: Position,
    @SerialName("type") val type: JcType,
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

@Serializable
@SerialName("ConstantValue")
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
@SerialName("NameMatcher")
sealed interface NameMatcher

@Serializable
@SerialName("NameIsEqualTo")
data class NameExactMatcher(
    @SerialName("name") val name: String,
) : NameMatcher

@Serializable
@SerialName("NameMatches")
data class NamePatternMatcher(
    @SerialName("pattern") val pattern: String,
) : NameMatcher

@Serializable
@SerialName("AnyNameMatches")
object AnyNameMatcher : NameMatcher

@Serializable
@SerialName("TypeMatcher")
sealed interface TypeMatcher

@Serializable
@SerialName("PrimitiveNameMatches")
data class PrimitiveNameMatcher(val name: String) : TypeMatcher

@Serializable
@SerialName("ClassMatcher")
data class ClassMatcher(
    @SerialName("packageMatcher") val pkg: NameMatcher,
    @SerialName("classNameMatcher") val classNameMatcher: NameMatcher,
) : TypeMatcher

@Serializable
@SerialName("AnyTypeMatches")
object AnyTypeMatcher : TypeMatcher

@Serializable
@SerialName("FunctionMatches")
data class FunctionMatcher(
    @SerialName("cls") val cls: ClassMatcher,
    @SerialName("functionName") val functionName: NameMatcher,
    @SerialName("parametersMatchers") val parametersMatchers: List<ParameterMatcher>,
    @SerialName("returnTypeMatcher") val returnTypeMatcher: TypeMatcher,
    @SerialName("applyToOverrides") val applyToOverrides: Boolean,
    @SerialName("functionLabel") val functionLabel: String?,
    @SerialName("modifier") val modifier: Int,
    @SerialName("exclude") val exclude: List<FunctionMatcher>,
)

@Serializable
@SerialName("ParameterMatches")
data class ParameterMatcher(
    @SerialName("index") val index: Int,
    @SerialName("typeMatcher") val typeMatcher: TypeMatcher,
)
