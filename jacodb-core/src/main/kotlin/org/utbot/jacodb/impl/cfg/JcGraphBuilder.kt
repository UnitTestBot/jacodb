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

package org.utbot.jacodb.impl.cfg

import org.utbot.jacodb.api.JcClassType
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.JcType
import org.utbot.jacodb.api.JcTypedMethod
import org.utbot.jacodb.api.TypeName
import org.utbot.jacodb.api.boolean
import org.utbot.jacodb.api.byte
import org.utbot.jacodb.api.cfg.BsmHandle
import org.utbot.jacodb.api.cfg.JcAddExpr
import org.utbot.jacodb.api.cfg.JcAndExpr
import org.utbot.jacodb.api.cfg.JcArgument
import org.utbot.jacodb.api.cfg.JcArrayAccess
import org.utbot.jacodb.api.cfg.JcAssignInst
import org.utbot.jacodb.api.cfg.JcBinaryExpr
import org.utbot.jacodb.api.cfg.JcBool
import org.utbot.jacodb.api.cfg.JcByte
import org.utbot.jacodb.api.cfg.JcCallExpr
import org.utbot.jacodb.api.cfg.JcCallInst
import org.utbot.jacodb.api.cfg.JcCastExpr
import org.utbot.jacodb.api.cfg.JcCatchInst
import org.utbot.jacodb.api.cfg.JcChar
import org.utbot.jacodb.api.cfg.JcClassConstant
import org.utbot.jacodb.api.cfg.JcCmpExpr
import org.utbot.jacodb.api.cfg.JcCmpgExpr
import org.utbot.jacodb.api.cfg.JcCmplExpr
import org.utbot.jacodb.api.cfg.JcConditionExpr
import org.utbot.jacodb.api.cfg.JcDivExpr
import org.utbot.jacodb.api.cfg.JcDouble
import org.utbot.jacodb.api.cfg.JcDynamicCallExpr
import org.utbot.jacodb.api.cfg.JcEnterMonitorInst
import org.utbot.jacodb.api.cfg.JcEqExpr
import org.utbot.jacodb.api.cfg.JcExitMonitorInst
import org.utbot.jacodb.api.cfg.JcExpr
import org.utbot.jacodb.api.cfg.JcFieldRef
import org.utbot.jacodb.api.cfg.JcFloat
import org.utbot.jacodb.api.cfg.JcGeExpr
import org.utbot.jacodb.api.cfg.JcGotoInst
import org.utbot.jacodb.api.cfg.JcGraph
import org.utbot.jacodb.api.cfg.JcGtExpr
import org.utbot.jacodb.api.cfg.JcIfInst
import org.utbot.jacodb.api.cfg.JcInst
import org.utbot.jacodb.api.cfg.JcInstList
import org.utbot.jacodb.api.cfg.JcInstLocation
import org.utbot.jacodb.api.cfg.JcInstRef
import org.utbot.jacodb.api.cfg.JcInstanceOfExpr
import org.utbot.jacodb.api.cfg.JcInt
import org.utbot.jacodb.api.cfg.JcLambdaExpr
import org.utbot.jacodb.api.cfg.JcLeExpr
import org.utbot.jacodb.api.cfg.JcLengthExpr
import org.utbot.jacodb.api.cfg.JcLocalVar
import org.utbot.jacodb.api.cfg.JcLong
import org.utbot.jacodb.api.cfg.JcLtExpr
import org.utbot.jacodb.api.cfg.JcMethodConstant
import org.utbot.jacodb.api.cfg.JcMulExpr
import org.utbot.jacodb.api.cfg.JcNegExpr
import org.utbot.jacodb.api.cfg.JcNeqExpr
import org.utbot.jacodb.api.cfg.JcNewArrayExpr
import org.utbot.jacodb.api.cfg.JcNewExpr
import org.utbot.jacodb.api.cfg.JcNullConstant
import org.utbot.jacodb.api.cfg.JcOrExpr
import org.utbot.jacodb.api.cfg.JcRawAddExpr
import org.utbot.jacodb.api.cfg.JcRawAndExpr
import org.utbot.jacodb.api.cfg.JcRawArgument
import org.utbot.jacodb.api.cfg.JcRawArrayAccess
import org.utbot.jacodb.api.cfg.JcRawAssignInst
import org.utbot.jacodb.api.cfg.JcRawBinaryExpr
import org.utbot.jacodb.api.cfg.JcRawBool
import org.utbot.jacodb.api.cfg.JcRawByte
import org.utbot.jacodb.api.cfg.JcRawCallExpr
import org.utbot.jacodb.api.cfg.JcRawCallInst
import org.utbot.jacodb.api.cfg.JcRawCastExpr
import org.utbot.jacodb.api.cfg.JcRawCatchInst
import org.utbot.jacodb.api.cfg.JcRawChar
import org.utbot.jacodb.api.cfg.JcRawClassConstant
import org.utbot.jacodb.api.cfg.JcRawCmpExpr
import org.utbot.jacodb.api.cfg.JcRawCmpgExpr
import org.utbot.jacodb.api.cfg.JcRawCmplExpr
import org.utbot.jacodb.api.cfg.JcRawDivExpr
import org.utbot.jacodb.api.cfg.JcRawDouble
import org.utbot.jacodb.api.cfg.JcRawDynamicCallExpr
import org.utbot.jacodb.api.cfg.JcRawEnterMonitorInst
import org.utbot.jacodb.api.cfg.JcRawEqExpr
import org.utbot.jacodb.api.cfg.JcRawExitMonitorInst
import org.utbot.jacodb.api.cfg.JcRawExprVisitor
import org.utbot.jacodb.api.cfg.JcRawFieldRef
import org.utbot.jacodb.api.cfg.JcRawFloat
import org.utbot.jacodb.api.cfg.JcRawGeExpr
import org.utbot.jacodb.api.cfg.JcRawGotoInst
import org.utbot.jacodb.api.cfg.JcRawGtExpr
import org.utbot.jacodb.api.cfg.JcRawIfInst
import org.utbot.jacodb.api.cfg.JcRawInst
import org.utbot.jacodb.api.cfg.JcRawInstVisitor
import org.utbot.jacodb.api.cfg.JcRawInstanceOfExpr
import org.utbot.jacodb.api.cfg.JcRawInt
import org.utbot.jacodb.api.cfg.JcRawInterfaceCallExpr
import org.utbot.jacodb.api.cfg.JcRawLabelInst
import org.utbot.jacodb.api.cfg.JcRawLabelRef
import org.utbot.jacodb.api.cfg.JcRawLeExpr
import org.utbot.jacodb.api.cfg.JcRawLengthExpr
import org.utbot.jacodb.api.cfg.JcRawLineNumberInst
import org.utbot.jacodb.api.cfg.JcRawLocalVar
import org.utbot.jacodb.api.cfg.JcRawLong
import org.utbot.jacodb.api.cfg.JcRawLtExpr
import org.utbot.jacodb.api.cfg.JcRawMethodConstant
import org.utbot.jacodb.api.cfg.JcRawMulExpr
import org.utbot.jacodb.api.cfg.JcRawNegExpr
import org.utbot.jacodb.api.cfg.JcRawNeqExpr
import org.utbot.jacodb.api.cfg.JcRawNewArrayExpr
import org.utbot.jacodb.api.cfg.JcRawNewExpr
import org.utbot.jacodb.api.cfg.JcRawNullConstant
import org.utbot.jacodb.api.cfg.JcRawOrExpr
import org.utbot.jacodb.api.cfg.JcRawRemExpr
import org.utbot.jacodb.api.cfg.JcRawReturnInst
import org.utbot.jacodb.api.cfg.JcRawShlExpr
import org.utbot.jacodb.api.cfg.JcRawShort
import org.utbot.jacodb.api.cfg.JcRawShrExpr
import org.utbot.jacodb.api.cfg.JcRawSpecialCallExpr
import org.utbot.jacodb.api.cfg.JcRawStaticCallExpr
import org.utbot.jacodb.api.cfg.JcRawStringConstant
import org.utbot.jacodb.api.cfg.JcRawSubExpr
import org.utbot.jacodb.api.cfg.JcRawSwitchInst
import org.utbot.jacodb.api.cfg.JcRawThis
import org.utbot.jacodb.api.cfg.JcRawThrowInst
import org.utbot.jacodb.api.cfg.JcRawUshrExpr
import org.utbot.jacodb.api.cfg.JcRawVirtualCallExpr
import org.utbot.jacodb.api.cfg.JcRawXorExpr
import org.utbot.jacodb.api.cfg.JcRemExpr
import org.utbot.jacodb.api.cfg.JcReturnInst
import org.utbot.jacodb.api.cfg.JcShlExpr
import org.utbot.jacodb.api.cfg.JcShort
import org.utbot.jacodb.api.cfg.JcShrExpr
import org.utbot.jacodb.api.cfg.JcSpecialCallExpr
import org.utbot.jacodb.api.cfg.JcStaticCallExpr
import org.utbot.jacodb.api.cfg.JcStringConstant
import org.utbot.jacodb.api.cfg.JcSubExpr
import org.utbot.jacodb.api.cfg.JcSwitchInst
import org.utbot.jacodb.api.cfg.JcThis
import org.utbot.jacodb.api.cfg.JcThrowInst
import org.utbot.jacodb.api.cfg.JcUshrExpr
import org.utbot.jacodb.api.cfg.JcValue
import org.utbot.jacodb.api.cfg.JcVirtualCallExpr
import org.utbot.jacodb.api.cfg.JcXorExpr
import org.utbot.jacodb.api.char
import org.utbot.jacodb.api.double
import org.utbot.jacodb.api.ext.anyType
import org.utbot.jacodb.api.ext.findTypeOrNull
import org.utbot.jacodb.api.ext.toType
import org.utbot.jacodb.api.float
import org.utbot.jacodb.api.int
import org.utbot.jacodb.api.long
import org.utbot.jacodb.api.short

/** This class stores state and is NOT THREAD SAFE. Use it carefully */
class JcGraphBuilder(
    val method: JcMethod,
    val instList: JcInstList<JcRawInst>
) : JcRawInstVisitor<JcInst?>, JcRawExprVisitor<JcExpr> {

    val classpath: JcClasspath = method.enclosingClass.classpath

    private val instMap = mutableMapOf<JcRawInst, JcInst>()
    private var currentLineNumber = 0
    private var index = 0
    private val labels = instList.filterIsInstance<JcRawLabelInst>().associateBy { it.ref }
    private val inst2Index: Map<JcRawInst, Int> = run {
        val res = mutableMapOf<JcRawInst, Int>()
        var index = 0
        for (inst in instList) {
            res[inst] = index
            if (inst !is JcRawLabelInst && inst !is JcRawLineNumberInst) ++index
        }
        res
    }

    private fun reset() {
        currentLineNumber = 0
        index = 0
    }

    fun buildFlowGraph(): JcGraph {
        return JcGraphImpl(method, instList.mapNotNull { convertRawInst(it) }).also {
            reset()
        }
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

    private val TypeName.asType
        get() = classpath.findTypeOrNull(this)
            ?: error("Could not find type $this")

    private fun label2InstRef(labelRef: JcRawLabelRef) =
        JcInstRef(inst2Index[labels.getValue(labelRef)]!!)

    override fun visitJcRawAssignInst(inst: JcRawAssignInst): JcInst = handle(inst) {
        val lhv = inst.lhv.accept(this) as JcValue
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
        val throwers = run {
            val result = mutableListOf<JcInstRef>()
            var current = instList.indexOf(labels.getValue(inst.startInclusive))
            val end = instList.indexOf(labels.getValue(inst.endExclusive))
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
        }
        return JcCatchInst(
            newLocation(),
            inst.throwable.accept(this) as JcValue,
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
        return JcInstLocation(method, index, currentLineNumber).also {
            index++
        }
    }

    private fun convertBinary(
        expr: JcRawBinaryExpr,
        handler: (JcType, JcValue, JcValue) -> JcBinaryExpr
    ): JcBinaryExpr {
        val type = expr.typeName.asType
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
        JcNegExpr(expr.typeName.asType, expr.operand.accept(this) as JcValue)

    override fun visitJcRawCastExpr(expr: JcRawCastExpr): JcExpr =
        JcCastExpr(expr.typeName.asType, expr.operand.accept(this) as JcValue)

    override fun visitJcRawNewExpr(expr: JcRawNewExpr): JcExpr = JcNewExpr(expr.typeName.asType)

    override fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr): JcExpr =
        JcNewArrayExpr(expr.typeName.asType, expr.dimensions.map { it.accept(this) as JcValue })

    override fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr): JcExpr =
        JcInstanceOfExpr(classpath.boolean, expr.operand.accept(this) as JcValue, expr.targetType.asType)

    private fun JcClassType.getMethod(name: String, argTypes: List<TypeName>, returnType: TypeName): JcTypedMethod {
        return methods.firstOrNull { typedMethod ->
            val jcMethod = typedMethod.method
            jcMethod.name == name &&
                    jcMethod.returnType.typeName == returnType.typeName &&
                    jcMethod.parameters.map { param -> param.type.typeName } == argTypes.map { it.typeName }
        }
            ?: error("Could not find a method with correct signature")
    }

    private val JcRawCallExpr.typedMethod: JcTypedMethod
        get() {
            val klass = declaringClass.asType as JcClassType
            return klass.getMethod(methodName, argumentTypes, returnType)
        }

    override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr): JcExpr {
        val lambdaBases = expr.bsmArgs.filterIsInstance<BsmHandle>()
        when (lambdaBases.size) {
            1 -> {
                val base = lambdaBases.first()
                val klass = base.declaringClass.asType as JcClassType
                val typedBase = klass.getMethod(base.name, base.argTypes, base.returnType)

                return JcLambdaExpr(typedBase, expr.args.map { it.accept(this) as JcValue })
            }

            else -> {
                val bsm = expr.typedMethod
                return JcDynamicCallExpr(
                    bsm,
                    expr.bsmArgs,
                    expr.callCiteMethodName,
                    expr.callCiteArgTypes.map { it.asType },
                    expr.callCiteReturnType.asType,
                    expr.args.map { it.accept(this) as JcValue }
                )
            }
        }
    }

    override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr): JcExpr {
        val instance = expr.instance.accept(this) as JcValue
        val klass = instance.type as? JcClassType ?: classpath.anyType()
        val method = klass.getMethod(expr.methodName, expr.argumentTypes, expr.returnType)
        val args = expr.args.map { it.accept(this) as JcValue }
        return JcVirtualCallExpr(
            method, instance, args
        )
    }

    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr): JcExpr {
        val instance = expr.instance.accept(this) as JcValue
        val klass = instance.type as? JcClassType ?: classpath.anyType()
        val method = klass.getMethod(expr.methodName, expr.argumentTypes, expr.returnType)
        val args = expr.args.map { it.accept(this) as JcValue }
        return JcVirtualCallExpr(
            method, instance, args
        )
    }

    override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr): JcExpr {
        val method = expr.typedMethod
        val args = expr.args.map { it.accept(this) as JcValue }
        return JcStaticCallExpr(method, args)
    }

    override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr): JcExpr {
        val method = expr.typedMethod
        val instance = expr.instance.accept(this) as JcValue
        val args = expr.args.map { it.accept(this) as JcValue }
        return JcSpecialCallExpr(
            method, instance, args,
        )
    }

    override fun visitJcRawThis(value: JcRawThis): JcExpr =
        JcThis(method.enclosingClass.toType())

    override fun visitJcRawArgument(value: JcRawArgument): JcExpr = method.parameters[value.index].let {
        JcArgument.of(it.index, value.name, it.type.asType)
    }

    override fun visitJcRawLocalVar(value: JcRawLocalVar): JcExpr =
        JcLocalVar(value.name, value.typeName.asType)

    override fun visitJcRawFieldRef(value: JcRawFieldRef): JcExpr {
        val instance = value.instance?.accept(this) as? JcValue
        val klass = (instance?.type ?: value.declaringClass.asType) as JcClassType
        val field =
            klass.fields.first { it.name == value.fieldName && it.field.type.typeName == value.typeName.typeName }
        return JcFieldRef(value.instance?.accept(this) as? JcValue, field)
    }

    override fun visitJcRawArrayAccess(value: JcRawArrayAccess): JcExpr =
        JcArrayAccess(
            value.array.accept(this) as JcValue,
            value.index.accept(this) as JcValue,
            value.typeName.asType
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
        JcNullConstant(classpath.anyType())

    override fun visitJcRawStringConstant(value: JcRawStringConstant): JcExpr =
        JcStringConstant(value.value, value.typeName.asType)

    override fun visitJcRawClassConstant(value: JcRawClassConstant): JcExpr =
        JcClassConstant(value.className.asType, value.typeName.asType)

    override fun visitJcRawMethodConstant(value: JcRawMethodConstant): JcExpr {
        val klass = value.declaringClass.asType as JcClassType
        val argumentTypes = value.argumentTypes.map { it.asType }
        val returnType = value.returnType.asType
        val constant = klass.declaredMethods.first {
            it.name == value.name && it.returnType == returnType && it.parameters.map { param -> param.type } == argumentTypes
        }
        return JcMethodConstant(constant, value.typeName.asType)
    }
}
