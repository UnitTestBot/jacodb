package org.utbot.jcdb.api.cfg

import org.utbot.jcdb.api.*
import org.utbot.jcdb.api.ext.*

class JcGraphBuilder(
    val classpath: JcClasspath,
    val instList: JcRawInstList,
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

    fun build(): JcGraph = JcGraph(classpath, instList.mapNotNull { convertRawInst(it) })

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

    private val JcClassOrInterface.asType get() = classpath.typeOf(this)

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
        val klass = instance.type as JcClassType
        val method = klass.getMethod(expr.methodName, expr.argumentTypes, expr.returnType)
        val args = expr.args.map { it.accept(this) as JcValue }
        return JcVirtualCallExpr(
            method, instance, args
        )
    }

    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr): JcExpr {
        val instance = expr.instance.accept(this) as JcValue
        val klass = instance.type as JcClassType
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
