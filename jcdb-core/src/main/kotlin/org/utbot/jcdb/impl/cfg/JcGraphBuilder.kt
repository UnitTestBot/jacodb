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

package org.utbot.jcdb.impl.cfg

import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.TypeName
import org.utbot.jcdb.api.boolean
import org.utbot.jcdb.api.byte
import org.utbot.jcdb.api.cfg.BsmHandle
import org.utbot.jcdb.api.cfg.JcAddExpr
import org.utbot.jcdb.api.cfg.JcAndExpr
import org.utbot.jcdb.api.cfg.JcArgument
import org.utbot.jcdb.api.cfg.JcArrayAccess
import org.utbot.jcdb.api.cfg.JcAssignInst
import org.utbot.jcdb.api.cfg.JcBinaryExpr
import org.utbot.jcdb.api.cfg.JcBool
import org.utbot.jcdb.api.cfg.JcByte
import org.utbot.jcdb.api.cfg.JcCallExpr
import org.utbot.jcdb.api.cfg.JcCallInst
import org.utbot.jcdb.api.cfg.JcCastExpr
import org.utbot.jcdb.api.cfg.JcCatchInst
import org.utbot.jcdb.api.cfg.JcChar
import org.utbot.jcdb.api.cfg.JcClassConstant
import org.utbot.jcdb.api.cfg.JcCmpExpr
import org.utbot.jcdb.api.cfg.JcCmpgExpr
import org.utbot.jcdb.api.cfg.JcCmplExpr
import org.utbot.jcdb.api.cfg.JcConditionExpr
import org.utbot.jcdb.api.cfg.JcDivExpr
import org.utbot.jcdb.api.cfg.JcDouble
import org.utbot.jcdb.api.cfg.JcDynamicCallExpr
import org.utbot.jcdb.api.cfg.JcEnterMonitorInst
import org.utbot.jcdb.api.cfg.JcEqExpr
import org.utbot.jcdb.api.cfg.JcExitMonitorInst
import org.utbot.jcdb.api.cfg.JcExpr
import org.utbot.jcdb.api.cfg.JcFieldRef
import org.utbot.jcdb.api.cfg.JcFloat
import org.utbot.jcdb.api.cfg.JcGeExpr
import org.utbot.jcdb.api.cfg.JcGotoInst
import org.utbot.jcdb.api.cfg.JcGraph
import org.utbot.jcdb.api.cfg.JcGtExpr
import org.utbot.jcdb.api.cfg.JcIfInst
import org.utbot.jcdb.api.cfg.JcInst
import org.utbot.jcdb.api.cfg.JcInstRef
import org.utbot.jcdb.api.cfg.JcInstanceOfExpr
import org.utbot.jcdb.api.cfg.JcInt
import org.utbot.jcdb.api.cfg.JcLambdaExpr
import org.utbot.jcdb.api.cfg.JcLeExpr
import org.utbot.jcdb.api.cfg.JcLengthExpr
import org.utbot.jcdb.api.cfg.JcLocal
import org.utbot.jcdb.api.cfg.JcLong
import org.utbot.jcdb.api.cfg.JcLtExpr
import org.utbot.jcdb.api.cfg.JcMethodConstant
import org.utbot.jcdb.api.cfg.JcMulExpr
import org.utbot.jcdb.api.cfg.JcNegExpr
import org.utbot.jcdb.api.cfg.JcNeqExpr
import org.utbot.jcdb.api.cfg.JcNewArrayExpr
import org.utbot.jcdb.api.cfg.JcNewExpr
import org.utbot.jcdb.api.cfg.JcNullConstant
import org.utbot.jcdb.api.cfg.JcOrExpr
import org.utbot.jcdb.api.cfg.JcRawAddExpr
import org.utbot.jcdb.api.cfg.JcRawAndExpr
import org.utbot.jcdb.api.cfg.JcRawArgument
import org.utbot.jcdb.api.cfg.JcRawArrayAccess
import org.utbot.jcdb.api.cfg.JcRawAssignInst
import org.utbot.jcdb.api.cfg.JcRawBinaryExpr
import org.utbot.jcdb.api.cfg.JcRawBool
import org.utbot.jcdb.api.cfg.JcRawByte
import org.utbot.jcdb.api.cfg.JcRawCallExpr
import org.utbot.jcdb.api.cfg.JcRawCallInst
import org.utbot.jcdb.api.cfg.JcRawCastExpr
import org.utbot.jcdb.api.cfg.JcRawCatchInst
import org.utbot.jcdb.api.cfg.JcRawChar
import org.utbot.jcdb.api.cfg.JcRawClassConstant
import org.utbot.jcdb.api.cfg.JcRawCmpExpr
import org.utbot.jcdb.api.cfg.JcRawCmpgExpr
import org.utbot.jcdb.api.cfg.JcRawCmplExpr
import org.utbot.jcdb.api.cfg.JcRawDivExpr
import org.utbot.jcdb.api.cfg.JcRawDouble
import org.utbot.jcdb.api.cfg.JcRawDynamicCallExpr
import org.utbot.jcdb.api.cfg.JcRawEnterMonitorInst
import org.utbot.jcdb.api.cfg.JcRawEqExpr
import org.utbot.jcdb.api.cfg.JcRawExitMonitorInst
import org.utbot.jcdb.api.cfg.JcRawExprVisitor
import org.utbot.jcdb.api.cfg.JcRawFieldRef
import org.utbot.jcdb.api.cfg.JcRawFloat
import org.utbot.jcdb.api.cfg.JcRawGeExpr
import org.utbot.jcdb.api.cfg.JcRawGotoInst
import org.utbot.jcdb.api.cfg.JcRawGtExpr
import org.utbot.jcdb.api.cfg.JcRawIfInst
import org.utbot.jcdb.api.cfg.JcRawInst
import org.utbot.jcdb.api.cfg.JcRawInstVisitor
import org.utbot.jcdb.api.cfg.JcRawInstanceOfExpr
import org.utbot.jcdb.api.cfg.JcRawInt
import org.utbot.jcdb.api.cfg.JcRawInterfaceCallExpr
import org.utbot.jcdb.api.cfg.JcRawLabelInst
import org.utbot.jcdb.api.cfg.JcRawLabelRef
import org.utbot.jcdb.api.cfg.JcRawLeExpr
import org.utbot.jcdb.api.cfg.JcRawLengthExpr
import org.utbot.jcdb.api.cfg.JcRawLocal
import org.utbot.jcdb.api.cfg.JcRawLong
import org.utbot.jcdb.api.cfg.JcRawLtExpr
import org.utbot.jcdb.api.cfg.JcRawMethodConstant
import org.utbot.jcdb.api.cfg.JcRawMulExpr
import org.utbot.jcdb.api.cfg.JcRawNegExpr
import org.utbot.jcdb.api.cfg.JcRawNeqExpr
import org.utbot.jcdb.api.cfg.JcRawNewArrayExpr
import org.utbot.jcdb.api.cfg.JcRawNewExpr
import org.utbot.jcdb.api.cfg.JcRawNullConstant
import org.utbot.jcdb.api.cfg.JcRawOrExpr
import org.utbot.jcdb.api.cfg.JcRawRemExpr
import org.utbot.jcdb.api.cfg.JcRawReturnInst
import org.utbot.jcdb.api.cfg.JcRawShlExpr
import org.utbot.jcdb.api.cfg.JcRawShort
import org.utbot.jcdb.api.cfg.JcRawShrExpr
import org.utbot.jcdb.api.cfg.JcRawSpecialCallExpr
import org.utbot.jcdb.api.cfg.JcRawStaticCallExpr
import org.utbot.jcdb.api.cfg.JcRawStringConstant
import org.utbot.jcdb.api.cfg.JcRawSubExpr
import org.utbot.jcdb.api.cfg.JcRawSwitchInst
import org.utbot.jcdb.api.cfg.JcRawThis
import org.utbot.jcdb.api.cfg.JcRawThrowInst
import org.utbot.jcdb.api.cfg.JcRawUshrExpr
import org.utbot.jcdb.api.cfg.JcRawVirtualCallExpr
import org.utbot.jcdb.api.cfg.JcRawXorExpr
import org.utbot.jcdb.api.cfg.JcRemExpr
import org.utbot.jcdb.api.cfg.JcReturnInst
import org.utbot.jcdb.api.cfg.JcShlExpr
import org.utbot.jcdb.api.cfg.JcShort
import org.utbot.jcdb.api.cfg.JcShrExpr
import org.utbot.jcdb.api.cfg.JcSpecialCallExpr
import org.utbot.jcdb.api.cfg.JcStaticCallExpr
import org.utbot.jcdb.api.cfg.JcStringConstant
import org.utbot.jcdb.api.cfg.JcSubExpr
import org.utbot.jcdb.api.cfg.JcSwitchInst
import org.utbot.jcdb.api.cfg.JcThis
import org.utbot.jcdb.api.cfg.JcThrowInst
import org.utbot.jcdb.api.cfg.JcUshrExpr
import org.utbot.jcdb.api.cfg.JcValue
import org.utbot.jcdb.api.cfg.JcVirtualCallExpr
import org.utbot.jcdb.api.cfg.JcXorExpr
import org.utbot.jcdb.api.char
import org.utbot.jcdb.api.double
import org.utbot.jcdb.api.ext.anyType
import org.utbot.jcdb.api.ext.findTypeOrNull
import org.utbot.jcdb.api.ext.toType
import org.utbot.jcdb.api.float
import org.utbot.jcdb.api.int
import org.utbot.jcdb.api.long
import org.utbot.jcdb.api.short

class JcGraphBuilder(
    val classpath: JcClasspath,
    val instList: JcRawInstListImpl,
    val method: JcMethod
) : JcRawInstVisitor<JcInst?>, JcRawExprVisitor<JcExpr> {
    private val instMap = mutableMapOf<JcRawInst, JcInst>()
    private val labels = instList.filterIsInstance<JcRawLabelInst>().associateBy { it.ref }
    private val inst2Index: Map<JcRawInst, Int> = run {
        val res = mutableMapOf<JcRawInst, Int>()
        var index = 0
        for (inst in instList) {
            res[inst] = index
            if (inst !is JcRawLabelInst) ++index
        }
        res
    }

    fun build(): JcGraph = JcGraphImpl(classpath, instList.mapNotNull { convertRawInst(it) })

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
        JcAssignInst(lhv, rhv)
    }

    override fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst): JcInst = handle(inst) {
        JcEnterMonitorInst(inst.monitor.accept(this) as JcValue)
    }

    override fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst): JcInst = handle(inst) {
        JcExitMonitorInst(inst.monitor.accept(this) as JcValue)
    }

    override fun visitJcRawCallInst(inst: JcRawCallInst): JcInst = handle(inst) {
        JcCallInst(inst.callExpr.accept(this) as JcCallExpr)
    }

    override fun visitJcRawLabelInst(inst: JcRawLabelInst): JcInst? {
        return null
    }

    override fun visitJcRawReturnInst(inst: JcRawReturnInst): JcInst {
        return JcReturnInst(inst.returnValue?.accept(this) as? JcValue)
    }

    override fun visitJcRawThrowInst(inst: JcRawThrowInst): JcInst {
        return JcThrowInst(inst.throwable.accept(this) as JcValue)
    }

    override fun visitJcRawCatchInst(inst: JcRawCatchInst): JcInst = handle(inst) {
        val throwers = run {
            val result = mutableListOf<JcInstRef>()
            var current = instList.indexOf(labels.getValue(inst.startInclusive))
            val end = instList.indexOf(labels.getValue(inst.endExclusive))
            while (current != end) {
                val jcInst = convertRawInst(instList[current])
                jcInst?.let {
                    result += JcInstRef(inst2Index[instList[current]]!!)
                }
                ++current
            }
            result
        }
        return JcCatchInst(
            inst.throwable.accept(this) as JcValue,
            throwers
        )
    }

    override fun visitJcRawGotoInst(inst: JcRawGotoInst): JcInst = handle(inst) {
        JcGotoInst(label2InstRef(inst.target))
    }

    override fun visitJcRawIfInst(inst: JcRawIfInst): JcInst = handle(inst) {
        JcIfInst(
            inst.condition.accept(this) as JcConditionExpr,
            label2InstRef(inst.trueBranch),
            label2InstRef(inst.falseBranch)
        )
    }

    override fun visitJcRawSwitchInst(inst: JcRawSwitchInst): JcInst = handle(inst) {
        JcSwitchInst(
            inst.key.accept(this) as JcValue,
            inst.branches.map { it.key.accept(this) as JcValue to label2InstRef(it.value) }.toMap(),
            label2InstRef(inst.default)
        )
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
        } ?: error("Could not find a method with correct signature")
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
        JcArgument(it.index, null, it.type.asType)
    }

    override fun visitJcRawLocal(value: JcRawLocal): JcExpr =
        JcLocal(value.name, value.typeName.asType)

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
        JcNullConstant(classpath.findTypeOrNull<Any>() ?: error("could not find type Any"))

    override fun visitJcRawStringConstant(value: JcRawStringConstant): JcExpr =
        JcStringConstant(value.value, value.typeName.asType)

    override fun visitJcRawClassConstant(value: JcRawClassConstant): JcExpr =
        JcClassConstant(value.className.asType as JcClassType, value.typeName.asType)

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
