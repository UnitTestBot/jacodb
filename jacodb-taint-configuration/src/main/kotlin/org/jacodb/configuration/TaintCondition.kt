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

import com.google.gson.annotations.SerializedName
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
}

interface Condition {
    fun <R> accept(conditionVisitor: ConditionVisitor<R>): R
}

data class And(@SerializedName("and") val conditions: List<Condition>) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class Or(@SerializedName("or") val conditions: List<Condition>) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class Not(@SerializedName("not") val condition: Condition) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class IsConstant(@SerializedName("isConstant") val position: Position) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class IsType(
    @SerializedName("position") val position: Position,
    @SerializedName("isType") val typeMatcher: TypeMatcher
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class AnnotationType(
    @SerializedName("position") val position: Position,
    @SerializedName("annotationType") val typeMatcher: TypeMatcher
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class ConstantEq(
    @SerializedName("position") val position: Position,
    @SerializedName("constantIsEqualTo") val value: ConstantValue
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class ConstantLt(
    @SerializedName("position") val position: Position,
    @SerializedName("lessThanConstant") val value: ConstantValue
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class ConstantGt(
    @SerializedName("position") val position: Position,
    @SerializedName("greaterThanConstant") val value: ConstantValue
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class ConstantMatches(
    @SerializedName("position") val position: Position,
    @SerializedName("matchesPattern") val pattern: String
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

data class SourceFunctionMatches(
    @SerializedName("position") val position: Position,
    @SerializedName("functionSource") val functionMatcher: FunctionMatcher
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

// sink label
data class CallParameterContainsMark(
    @SerializedName("position") val position: Position,
    @SerializedName("containsMark") val mark: TaintMark
) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

object ConstantTrue : Condition {
    override fun toString(): String = "ConstantTrue"

    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}

// Extra conditions
data class TypeMatches(val position: Position, val type: JcType) : Condition {
    override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R = conditionVisitor.visit(this)
}


sealed interface ConstantValue
data class ConstantIntValue(val value: Int) : ConstantValue
data class ConstantBooleanValue(val value: Boolean) : ConstantValue
data class ConstantStringValue(val value: String) : ConstantValue

sealed interface NameMatcher
data class NamePatternMatcher(val pattern: String) : NameMatcher
data class NameExactMatcher(val name: String) : NameMatcher
object AnyNameMatcher : NameMatcher {
    override fun toString(): String = "AnyNameMatches"
}

sealed interface TypeMatcher
data class ClassMatcher(val pkg: NameMatcher, val classNameMatcher: NameMatcher) : TypeMatcher
data class PrimitiveNameMatcher(val name: String) : TypeMatcher
object AnyTypeMatcher : TypeMatcher {
    override fun toString(): String = "AnyTypeMatches"
}

data class FunctionMatcher(
    val cls: ClassMatcher,
    val functionName: NameMatcher,
    val parametersMatchers: List<ParameterMatcher>,
    val returnTypeMatcher: TypeMatcher,
    val applyToOverrides: Boolean,
    val functionLabel: String?,
    val modifier: Int,
    val exclude: List<FunctionMatcher>
)

data class ParameterMatcher(val index: Int, val typeMatcher: TypeMatcher)
