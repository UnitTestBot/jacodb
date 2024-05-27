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

package org.jacodb.impl.cfg

import org.jacodb.api.jvm.*
import org.jacodb.api.jvm.cfg.*
import org.jacodb.api.jvm.ext.*
import org.jacodb.impl.cfg.util.UNINIT_THIS
import org.jacodb.impl.cfg.util.lambdaMetaFactory
import org.jacodb.impl.cfg.util.lambdaMetaFactoryMethodName

/** This class stores state and is NOT THREAD SAFE. Use it carefully */
class JcInstListBuilder(val method: JcMethod,val instList: JcInstList<JcRawInst>) : JcRawInstVisitor<JcInst?>, JcRawExprVisitor<JcExpr> {

    val classpath: JcClasspath = method.enclosingClass.classpath

    private val instMap = identityMap<JcRawInst, JcInst>()
    private var currentLineNumber = 0
    private var index = 0
    private val labels = instList.filterIsInstance<JcRawLabelInst>().associateBy { it.ref }
    private val convertedLocalVars = mutableMapOf<JcRawLocalVar, JcRawLocalVar>()
    private val inst2Index: Map<JcRawInst, Int> = identityMap<JcRawInst, Int>().also {
        var index = 0
        for (inst in instList) {
            it[inst] = index
            if (inst !is JcRawLabelInst && inst !is JcRawLineNumberInst) ++index
        }
    }

    private fun reset() {
        currentLineNumber = 0
        index = 0
    }

    fun buildInstList(): JcInstList<JcInst> {
        return JcInstListImpl(instList.mapNotNull { convertRawInst(it) }).also {
            reset()
        }
    }

    private inline fun <reified T : JcRawInst> handle(inst: T, handler: () -> JcInst) =
        instMap.getOrPut(inst) { handler() }

    private fun convertRawInst(rawInst: JcRawInst): JcInst? = when (rawInst) {
        in instMap -> instMap[rawInst]!!
        else -> {
            val jcInst = rawInst.accept(this)
            if (jcInst != null) {
                instMap[rawInst] = jcInst
            }
            jcInst
        }
    }

    private fun TypeName.asType() = classpath.findTypeOrNull(this)
        ?: error("Could not find type $this")

    private fun label2InstRef(labelRef: JcRawLabelRef) =
        JcInstRef(inst2Index[labels.getValue(labelRef)]!!)

    override fun visitJcRawAssignInst(inst: JcRawAssignInst): JcInst = handle(inst) {
        val preprocessedLhv =
            inst.lhv.let { unprocessedLhv ->
                if (unprocessedLhv is JcRawLocalVar && unprocessedLhv.typeName == UNINIT_THIS) {
                    convertedLocalVars.getOrPut(unprocessedLhv) {
                        JcRawLocalVar(unprocessedLhv.index, unprocessedLhv.name, inst.rhv.typeName)
                    }
                } else {
                    unprocessedLhv
                }
            }
        val lhv = preprocessedLhv.accept(this) as JcValue
        val rhv = inst.rhv.accept(this)
        JcAssignInst(newLocation(), lhv, rhv)
    }

    override fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst): JcInst = handle(inst) {
        JcEnterMonitorInst(newLocation(), inst.monitor.accept(this) as JcValue)
    }

    override fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst): JcInst = handle(inst) {
        JcExitMonitorInst(newLocation(), inst.monitor.accept(this) as JcValue)
    }

    override fun visitJcRawCallInst(inst: JcRawCallInst): JcInst = handle(inst) {
        JcCallInst(newLocation(), inst.callExpr.accept(this) as JcCallExpr)
    }

    override fun visitJcRawLabelInst(inst: JcRawLabelInst): JcInst? {
        return null
    }

    override fun visitJcRawLineNumberInst(inst: JcRawLineNumberInst): JcInst? {
        currentLineNumber = inst.lineNumber
        return null
    }

    override fun visitJcRawReturnInst(inst: JcRawReturnInst): JcInst {
        return JcReturnInst(newLocation(), inst.returnValue?.accept(this) as? JcValue)
    }

    override fun visitJcRawThrowInst(inst: JcRawThrowInst): JcInst {
        return JcThrowInst(newLocation(), inst.throwable.accept(this) as JcValue)
    }

    override fun visitJcRawCatchInst(inst: JcRawCatchInst): JcInst = handle(inst) {
        val location = newLocation()
        val throwableTypes = inst.entries.map { it.acceptedThrowable.asType() }
        val throwers = inst.entries.flatMap {
            val result = mutableListOf<JcInstRef>()
            var current = instList.indexOf(labels.getValue(it.startInclusive))
            val end = instList.indexOf(labels.getValue(it.endExclusive))
            while (current != end) {
                val rawInst = instList[current]
                if (rawInst != inst) {
                    val jcInst = convertRawInst(rawInst)
                    jcInst?.let {
                        result += JcInstRef(inst2Index[rawInst]!!)
                    }
                }
                ++current
            }
            result
        }.distinct()

        return JcCatchInst(
            location,
            inst.throwable.accept(this) as JcValue,
            throwableTypes,
            throwers
        )
    }

    override fun visitJcRawGotoInst(inst: JcRawGotoInst): JcInst = handle(inst) {
        JcGotoInst(newLocation(), label2InstRef(inst.target))
    }

    override fun visitJcRawIfInst(inst: JcRawIfInst): JcInst = handle(inst) {
        JcIfInst(
            newLocation(),
            inst.condition.accept(this) as JcConditionExpr,
            label2InstRef(inst.trueBranch),
            label2InstRef(inst.falseBranch)
        )
    }

    override fun visitJcRawSwitchInst(inst: JcRawSwitchInst): JcInst = handle(inst) {
        JcSwitchInst(
            newLocation(),
            inst.key.accept(this) as JcValue,
            inst.branches.map { it.key.accept(this) as JcValue to label2InstRef(it.value) }.toMap(),
            label2InstRef(inst.default)
        )
    }

    private fun newLocation(): JcInstLocation {
        return JcInstLocationImpl(method, index, currentLineNumber).also {
            index++
        }
    }

    private fun convertBinary(
        expr: JcRawBinaryExpr,
        handler: (JcType, JcValue, JcValue) -> JcBinaryExpr
    ): JcBinaryExpr {
        val type = expr.typeName.asType()
        val lhv = expr.lhv.accept(this) as JcValue
        val rhv = expr.rhv.accept(this) as JcValue
        return handler(type, lhv, rhv)
    }

    override fun visitJcRawAddExpr(expr: JcRawAddExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcAddExpr(type, lhv, rhv) }

    override fun visitJcRawAndExpr(expr: JcRawAndExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcAndExpr(type, lhv, rhv) }

    override fun visitJcRawCmpExpr(expr: JcRawCmpExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcCmpExpr(type, lhv, rhv) }

    override fun visitJcRawCmpgExpr(expr: JcRawCmpgExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcCmpgExpr(type, lhv, rhv) }

    override fun visitJcRawCmplExpr(expr: JcRawCmplExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcCmplExpr(type, lhv, rhv) }

    override fun visitJcRawDivExpr(expr: JcRawDivExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcDivExpr(type, lhv, rhv) }

    override fun visitJcRawMulExpr(expr: JcRawMulExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcMulExpr(type, lhv, rhv) }

    override fun visitJcRawEqExpr(expr: JcRawEqExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcEqExpr(type, lhv, rhv) }

    override fun visitJcRawNeqExpr(expr: JcRawNeqExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcNeqExpr(type, lhv, rhv) }

    override fun visitJcRawGeExpr(expr: JcRawGeExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcGeExpr(type, lhv, rhv) }

    override fun visitJcRawGtExpr(expr: JcRawGtExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcGtExpr(type, lhv, rhv) }

    override fun visitJcRawLeExpr(expr: JcRawLeExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcLeExpr(type, lhv, rhv) }

    override fun visitJcRawLtExpr(expr: JcRawLtExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcLtExpr(type, lhv, rhv) }

    override fun visitJcRawOrExpr(expr: JcRawOrExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcOrExpr(type, lhv, rhv) }

    override fun visitJcRawRemExpr(expr: JcRawRemExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcRemExpr(type, lhv, rhv) }

    override fun visitJcRawShlExpr(expr: JcRawShlExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcShlExpr(type, lhv, rhv) }

    override fun visitJcRawShrExpr(expr: JcRawShrExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcShrExpr(type, lhv, rhv) }

    override fun visitJcRawSubExpr(expr: JcRawSubExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcSubExpr(type, lhv, rhv) }

    override fun visitJcRawUshrExpr(expr: JcRawUshrExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcUshrExpr(type, lhv, rhv) }

    override fun visitJcRawXorExpr(expr: JcRawXorExpr): JcExpr =
        convertBinary(expr) { type, lhv, rhv -> JcXorExpr(type, lhv, rhv) }

    override fun visitJcRawLengthExpr(expr: JcRawLengthExpr): JcExpr {
        return JcLengthExpr(classpath.int, expr.array.accept(this) as JcValue)
    }

    override fun visitJcRawNegExpr(expr: JcRawNegExpr): JcExpr =
        JcNegExpr(expr.typeName.asType(), expr.operand.accept(this) as JcValue)

    override fun visitJcRawCastExpr(expr: JcRawCastExpr): JcExpr =
        JcCastExpr(expr.typeName.asType(), expr.operand.accept(this) as JcValue)

    override fun visitJcRawNewExpr(expr: JcRawNewExpr): JcExpr = JcNewExpr(expr.typeName.asType())

    override fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr): JcExpr =
        JcNewArrayExpr(expr.typeName.asType(), expr.dimensions.map { it.accept(this) as JcValue })

    override fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr): JcExpr =
        JcInstanceOfExpr(classpath.boolean, expr.operand.accept(this) as JcValue, expr.targetType.asType())

    override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr): JcExpr {
        if (expr.bsm.declaringClass == lambdaMetaFactory && expr.bsm.name == lambdaMetaFactoryMethodName) {
            val lambdaExpr = tryResolveJcLambdaExpr(expr)
            if (lambdaExpr != null) return lambdaExpr
        }

        return JcDynamicCallExpr(
            classpath.methodRef(expr),
            expr.bsmArgs,
            expr.callSiteMethodName,
            expr.callSiteArgTypes.map { it.asType() },
            expr.callSiteReturnType.asType(),
            expr.callSiteArgs.map { it.accept(this) as JcValue }
        )
    }

    private fun tryResolveJcLambdaExpr(expr: JcRawDynamicCallExpr): JcLambdaExpr? {
        if (expr.bsmArgs.size != 3) return null
        val (interfaceMethodType, implementation, dynamicMethodType) = expr.bsmArgs

        if (interfaceMethodType !is BsmMethodTypeArg) return null
        if (dynamicMethodType !is BsmMethodTypeArg) return null
        if (implementation !is BsmHandle) return null

        val argTypes: List<TypeName>
        val tag = implementation.tag
        if (tag == 6) {
            // Invoke static case
            argTypes = implementation.argTypes
        } else {
            // Invoke non-static case
            check(tag == 5 || tag == 7 || tag == 8 || tag == 9) {
                "Unexpected tag for invoke dynamic $tag"
            }
            argTypes = implementation.argTypes.toMutableList()
            // Adding 'this' type as first argument type
            argTypes.add(0, implementation.declaringClass)
        }

        // Check implementation signature match (starts with) call site arguments
        for ((index, argType) in expr.callSiteArgTypes.withIndex()) {
            if (argType != argTypes.getOrNull(index)) return null
        }

        val klass = implementation.declaringClass.asType() as JcClassType
        val actualMethod = TypedMethodRefImpl(
            klass, implementation.name, implementation.argTypes, implementation.returnType
        )

        return JcLambdaExpr(
            classpath.methodRef(expr),
            actualMethod,
            interfaceMethodType,
            dynamicMethodType,
            expr.callSiteMethodName,
            expr.callSiteArgTypes.map { it.asType() },
            expr.callSiteReturnType.asType(),
            expr.callSiteArgs.map { it.accept(this) as JcValue }
        )
    }

    override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr): JcExpr {
        val instance = expr.instance.accept(this) as JcValue
        val args = expr.args.map { it.accept(this) as JcValue }
        return JcVirtualCallExpr(VirtualMethodRefImpl.of(classpath, expr), instance, args)
    }

    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr): JcExpr {
        val instance = expr.instance.accept(this) as JcValue
        val args = expr.args.map { it.accept(this) as JcValue }
        return JcVirtualCallExpr(VirtualMethodRefImpl.of(classpath, expr), instance, args)
    }

    override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr): JcExpr {
        val args = expr.args.map { it.accept(this) as JcValue }
        return JcStaticCallExpr(classpath.methodRef(expr), args)
    }

    override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr): JcExpr {
        val instance = expr.instance.accept(this) as JcValue
        val args = expr.args.map { it.accept(this) as JcValue }
        return JcSpecialCallExpr(classpath.methodRef(expr), instance, args)
    }

    override fun visitJcRawThis(value: JcRawThis): JcExpr = JcThis(method.enclosingClass.toType())

    override fun visitJcRawArgument(value: JcRawArgument): JcExpr = method.parameters[value.index].let {
        JcArgument.of(it.index, value.name, it.type.asType())
    }

    override fun visitJcRawLocalVar(value: JcRawLocalVar): JcExpr =
        convertedLocalVars[value]?.let { replacementForLocalVar ->
            JcLocalVar(replacementForLocalVar.index, replacementForLocalVar.name, replacementForLocalVar.typeName.asType())
        } ?: JcLocalVar(value.index, value.name, value.typeName.asType())

    override fun visitJcRawFieldRef(value: JcRawFieldRef): JcExpr {
        val type = value.declaringClass.asType() as JcClassType
        val field = type.lookup.field(value.fieldName, value.typeName)
            ?: throw IllegalStateException("${type.typeName}#${value.fieldName} not found")
        return JcFieldRef(value.instance?.accept(this) as? JcValue, field)
    }

    override fun visitJcRawArrayAccess(value: JcRawArrayAccess): JcExpr =
        JcArrayAccess(
            value.array.accept(this) as JcValue,
            value.index.accept(this) as JcValue,
            value.typeName.asType()
        )

    override fun visitJcRawBool(value: JcRawBool): JcExpr = JcBool(value.value, classpath.boolean)

    override fun visitJcRawByte(value: JcRawByte): JcExpr = JcByte(value.value, classpath.byte)

    override fun visitJcRawChar(value: JcRawChar): JcExpr = JcChar(value.value, classpath.char)

    override fun visitJcRawShort(value: JcRawShort): JcExpr = JcShort(value.value, classpath.short)

    override fun visitJcRawInt(value: JcRawInt): JcExpr = JcInt(value.value, classpath.int)

    override fun visitJcRawLong(value: JcRawLong): JcExpr = JcLong(value.value, classpath.long)

    override fun visitJcRawFloat(value: JcRawFloat): JcExpr = JcFloat(value.value, classpath.float)

    override fun visitJcRawDouble(value: JcRawDouble): JcExpr = JcDouble(value.value, classpath.double)

    override fun visitJcRawNullConstant(value: JcRawNullConstant): JcExpr =
        JcNullConstant(classpath.objectType)

    override fun visitJcRawStringConstant(value: JcRawStringConstant): JcExpr =
        JcStringConstant(value.value, value.typeName.asType())

    override fun visitJcRawClassConstant(value: JcRawClassConstant): JcExpr =
        JcClassConstant(value.className.asType(), value.typeName.asType())

    override fun visitJcRawMethodConstant(value: JcRawMethodConstant): JcExpr {
        val klass = value.declaringClass.asType() as JcClassType
        val argumentTypes = value.argumentTypes.map { it.asType() }
        val returnType = value.returnType.asType()
        val constant = klass.declaredMethods.first {
            it.name == value.name && it.returnType == returnType && it.parameters.map { param -> param.type } == argumentTypes
        }
        return JcMethodConstant(constant, value.typeName.asType())
    }

    override fun visitJcRawMethodType(value: JcRawMethodType): JcExpr {
        return JcMethodType(
            value.argumentTypes.map { it.asType() },
            value.returnType.asType(),
            value.typeName.asType()
        )
    }
}
