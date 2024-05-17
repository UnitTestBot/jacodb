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

package org.jacodb.analysis.ifds

import io.mockk.every
import io.mockk.mockk
import org.jacodb.analysis.ifds.config.BasicConditionEvaluator
import org.jacodb.analysis.ifds.config.FactAwareConditionEvaluator
import org.jacodb.analysis.ifds.taint.Tainted
import org.jacodb.analysis.ifds.util.Maybe
import org.jacodb.analysis.ifds.util.toMaybe
import org.jacodb.analysis.ifds.util.toPath
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcType
import org.jacodb.api.PredefinedPrimitive
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.cfg.JcBool
import org.jacodb.api.cfg.JcInt
import org.jacodb.api.cfg.JcStringConstant
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcValue
import org.jacodb.taint.configuration.And
import org.jacodb.taint.configuration.AnnotationType
import org.jacodb.taint.configuration.Argument
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
import org.jacodb.taint.configuration.Position
import org.jacodb.taint.configuration.SourceFunctionMatches
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.This
import org.jacodb.taint.configuration.TypeMatches
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConditionEvaluatorTest {

    private val cp = mockk<JcClasspath>()

    private val intType: JcPrimitiveType = PredefinedPrimitive(cp, PredefinedPrimitives.Int)
    private val boolType: JcPrimitiveType = PredefinedPrimitive(cp, PredefinedPrimitives.Boolean)
    private val stringType = mockk<JcType> {
        every { classpath } returns cp
    }

    private val intArg: Position = Argument(0)
    private val intValue = JcInt(42, intType)

    private val boolArg: Position = Argument(1)
    private val boolValue = JcBool(true, boolType)

    private val stringArg: Position = Argument(2)
    private val stringValue = JcStringConstant("test", stringType)

    private val thisPos: Position = This
    private val thisValue = JcThis(type = mockk())

    private val positionResolver: (position: Position) -> Maybe<JcValue> = { position ->
        when (position) {
            intArg -> intValue
            boolArg -> boolValue
            stringArg -> stringValue
            thisPos -> thisValue
            else -> null
        }.toMaybe()
    }
    private val evaluator: ConditionVisitor<Boolean> = BasicConditionEvaluator(positionResolver)

    @Test
    fun `True is true`() {
        val condition = ConstantTrue
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `Not(True) is false`() {
        val condition = Not(ConstantTrue)
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `Not(Not(True)) is true`() {
        val condition = Not(Not(ConstantTrue))
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `And(True) is true`() {
        val condition = And(listOf(ConstantTrue, ConstantTrue, ConstantTrue))
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `And(Not(True)) is false`() {
        val condition = And(listOf(ConstantTrue, ConstantTrue, Not(ConstantTrue)))
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `Or(Not(True)) is false`() {
        val condition = Or(listOf(Not(ConstantTrue), Not(ConstantTrue), Not(ConstantTrue)))
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `Or(True) is true`() {
        val condition = Or(listOf(Not(ConstantTrue), Not(ConstantTrue), ConstantTrue))
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `IsConstant(int) is true`() {
        val condition = IsConstant(intArg)
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `IsConstant(bool) is true`() {
        val condition = IsConstant(boolArg)
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `IsConstant(this) is false`() {
        val condition = IsConstant(thisPos)
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `IsConstant(unresolved) is false`() {
        val condition = IsConstant(position = mockk())
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `IsType in unexpected`() {
        val condition = mockk<IsType>()
        assertFailsWith<IllegalStateException> {
            evaluator.visit(condition)
        }
    }

    @Test
    fun `AnnotationType in unexpected`() {
        val condition = mockk<AnnotationType>()
        assertFailsWith<IllegalStateException> {
            evaluator.visit(condition)
        }
    }

    @Test
    fun `ConstantEq(intArg(42), 42) is true`() {
        val condition = ConstantEq(intArg, ConstantIntValue(42))
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `ConstantEq(intArg(42), 999) is false`() {
        val condition = ConstantEq(intArg, ConstantIntValue(999))
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `ConstantEq(boolArg(true), true) is true`() {
        val condition = ConstantEq(boolArg, ConstantBooleanValue(true))
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `ConstantEq(boolArg(true), false) is false`() {
        val condition = ConstantEq(boolArg, ConstantBooleanValue(false))
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `ConstantEq(stringArg('test'), 'test') is true`() {
        val condition = ConstantEq(stringArg, ConstantStringValue("test"))
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `ConstantEq(stringArg('test'), 'other') is false`() {
        val condition = ConstantEq(stringArg, ConstantStringValue("other"))
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `ConstantEq(unresolved, any) is false`() {
        val condition = ConstantEq(position = mockk(), value = mockk())
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `ConstantLt(intArg(42), 999) is true`() {
        val condition = ConstantLt(intArg, ConstantIntValue(999))
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `ConstantLt(intArg(42), 5) is false`() {
        val condition = ConstantLt(intArg, ConstantIntValue(5))
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `ConstantLt(unresolved, any) is false`() {
        val condition = ConstantLt(position = mockk(), value = mockk())
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `ConstantGt(intArg(42), 5) is true`() {
        val condition = ConstantGt(intArg, ConstantIntValue(5))
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `ConstantGt(intArg(42), 999) is false`() {
        val condition = ConstantGt(intArg, ConstantIntValue(999))
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `ConstantGt(unresolved, any) is false`() {
        val condition = ConstantGt(position = mockk(), value = mockk())
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `ConstantMatches(intArg(42), '42') is true`() {
        val condition = ConstantMatches(intArg, "42")
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `ConstantMatches(intArg(42), 'd+') is true`() {
        val condition = ConstantMatches(intArg, "\\d+")
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `ConstantMatches(stringArg('test'), 'test') is true`() {
        val condition = ConstantMatches(stringArg, "\"test\"")
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `ConstantMatches(stringArg('test'), 'w+') is true`() {
        val condition = ConstantMatches(stringArg, "\"\\w+\"")
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `ConstantMatches(unresolved, any) is false`() {
        val condition = ConstantMatches(position = mockk(), pattern = ".*")
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `SourceFunctionMatches is not implemented yet`() {
        val condition = mockk<SourceFunctionMatches>()
        assertFailsWith<NotImplementedError> {
            evaluator.visit(condition)
        }
    }

    @Test
    fun `ContainsMark is not supported by basic evaluator`() {
        val condition = mockk<ContainsMark>()
        assertFailsWith<IllegalStateException> {
            evaluator.visit(condition)
        }
    }

    @Test
    fun `TypeMatches(intArg, Int) is true`() {
        val condition = TypeMatches(intArg, intType)
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `TypeMatches(boolArg, Boolean) is true`() {
        val condition = TypeMatches(boolArg, boolType)
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `TypeMatches(stringArg, String) is true`() {
        val condition = TypeMatches(stringArg, stringType)
        assertTrue(evaluator.visit(condition))
    }

    @Test
    fun `TypeMatches(unresolved, any) is false`() {
        val condition = TypeMatches(position = mockk(), type = mockk())
        assertFalse(evaluator.visit(condition))
    }

    @Test
    fun `FactAwareConditionEvaluator supports ContainsMark`() {
        val fact = Tainted(intValue.toPath(), TaintMark("FOO"))
        val factAwareEvaluator = FactAwareConditionEvaluator(fact, positionResolver)
        assertTrue(factAwareEvaluator.visit(ContainsMark(intArg, TaintMark("FOO"))))
        assertFalse(factAwareEvaluator.visit(ContainsMark(intArg, TaintMark("BAR"))))
        assertFalse(factAwareEvaluator.visit(ContainsMark(stringArg, TaintMark("FOO"))))
        assertFalse(factAwareEvaluator.visit(ContainsMark(stringArg, TaintMark("BAR"))))
        assertFalse(factAwareEvaluator.visit(ContainsMark(position = mockk(), TaintMark("FOO"))))
    }
}
