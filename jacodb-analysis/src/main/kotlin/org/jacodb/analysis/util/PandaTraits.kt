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

package org.jacodb.analysis.util

import org.jacodb.analysis.ifds.AccessPath
import org.jacodb.analysis.ifds.ElementAccessor
import org.jacodb.analysis.ifds.FieldAccessor
import org.jacodb.analysis.util.getArgument
import org.jacodb.analysis.util.toPathOrNull
import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.api.common.CommonProject
import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaArgument
import org.jacodb.panda.dynamic.api.PandaArrayAccess
import org.jacodb.panda.dynamic.api.PandaBoolConstant
import org.jacodb.panda.dynamic.api.PandaCallExpr
import org.jacodb.panda.dynamic.api.PandaCastExpr
import org.jacodb.panda.dynamic.api.PandaClass
import org.jacodb.panda.dynamic.api.PandaClassType
import org.jacodb.panda.dynamic.api.PandaConstant
import org.jacodb.panda.dynamic.api.PandaExpr
import org.jacodb.panda.dynamic.api.PandaFieldRef
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaLoadedValue
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaMethodParameter
import org.jacodb.panda.dynamic.api.PandaNumberConstant
import org.jacodb.panda.dynamic.api.PandaProject
import org.jacodb.panda.dynamic.api.PandaSimpleValue
import org.jacodb.panda.dynamic.api.PandaStringConstant
import org.jacodb.panda.dynamic.api.PandaThis
import org.jacodb.panda.dynamic.api.PandaValue
import org.jacodb.panda.dynamic.api.PandaValueByInstance
import org.jacodb.taint.configuration.ConstantBooleanValue
import org.jacodb.taint.configuration.ConstantIntValue
import org.jacodb.taint.configuration.ConstantStringValue
import org.jacodb.taint.configuration.ConstantValue
import org.jacodb.analysis.util.getArgument as _getArgument
import org.jacodb.analysis.util.getArgumentsOf as _getArgumentsOf
import org.jacodb.analysis.util.thisInstance as _thisInstance
import org.jacodb.analysis.util.toPath as _toPath
import org.jacodb.analysis.util.toPathOrNull as _toPathOrNull
import org.jacodb.panda.dynamic.api.callExpr as _callExpr

/**
 * Panda-specific extensions for analysis.
 *
 * ### Usage:
 * ```
 * class MyClass {
 *     companion object : PandaTraits
 * }
 * ```
 */
interface PandaTraits : Traits<PandaMethod, PandaInst> {

    override val PandaMethod.thisInstance: PandaThis
        get() = _thisInstance

    // TODO
    override val PandaMethod.isConstructor: Boolean
        get() = false

    override fun CommonExpr.toPathOrNull(): AccessPath? {
        check(this is PandaExpr)
        return _toPathOrNull()
    }

    override fun CommonValue.toPathOrNull(): AccessPath? {
        check(this is PandaValue)
        return _toPathOrNull()
    }

    override fun CommonValue.toPath(): AccessPath {
        check(this is PandaValue)
        return _toPath()
    }

    override val CommonCallExpr.callee: PandaMethod
        get() {
            check(this is PandaCallExpr)
            return method
        }

    override fun PandaInst.getCallExpr(): CommonCallExpr? = _callExpr

    override fun CommonProject.getArgument(param: CommonMethodParameter): PandaArgument {
        check(this is PandaProject)
        check(param is PandaMethodParameter)
        return _getArgument(param)
    }

    override fun CommonProject.getArgumentsOf(method: PandaMethod): List<PandaArgument> {
        check(this is PandaProject)
        return _getArgumentsOf(method)
    }

    override fun CommonValue.isConstant(): Boolean {
        check(this is PandaValue)
        return this is PandaConstant
    }

    override fun CommonValue.eqConstant(constant: ConstantValue): Boolean {
        check(this is PandaValue)
        return when (constant) {
            is ConstantBooleanValue -> {
                this is PandaBoolConstant && this.value == constant.value
            }

            is ConstantIntValue -> {
                this is PandaNumberConstant && this.value == constant.value
            }

            is ConstantStringValue -> {
                // TODO: convert to string if necessary
                this is PandaStringConstant && this.value == constant.value
            }
        }
    }

    override fun CommonValue.ltConstant(constant: ConstantValue): Boolean {
        check(this is PandaValue)
        return when (constant) {
            is ConstantIntValue -> {
                this is PandaNumberConstant && this.value < constant.value
            }

            else -> false
        }
    }

    override fun CommonValue.gtConstant(constant: ConstantValue): Boolean {
        check(this is PandaValue)
        return when (constant) {
            is ConstantIntValue -> {
                this is PandaNumberConstant && this.value > constant.value
            }

            else -> false
        }
    }

    override fun CommonValue.matches(pattern: String): Boolean {
        check(this is PandaValue)
        val s = this.toString()
        val re = pattern.toRegex()
        return re.matches(s)
    }

    override fun CommonExpr.getValues(): Set<CommonValue> {
        check(this is PandaExpr)
        return TODO()
    }

    override fun PandaInst.getOperands(): List<CommonExpr> {
        return operands
    }

    // Ensure that all methods are default-implemented in the interface itself:
    companion object : PandaTraits
}

val PandaMethod.thisInstance: PandaThis
    // get() = PandaThis(enclosingClass.toType())
    get() = PandaThis(PandaAnyType)

internal fun PandaClass.toType(): PandaClassType {
    TODO("return project.classTypeOf(this)")
}

fun PandaExpr.toPathOrNull(): AccessPath? = when (this) {
    is PandaValue -> toPathOrNull()
    is PandaCastExpr -> operand.toPathOrNull()
    else -> null
}

fun PandaValue.toPathOrNull(): AccessPath? = when (this) {
    is PandaSimpleValue -> AccessPath(this, emptyList())

    is PandaArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is PandaFieldRef -> {
        val instance = instance
        if (instance == null) {
            AccessPath(null, listOf(FieldAccessor(classField.name, isStatic = true)))
        } else {
            instance.toPathOrNull()?.let {
                it + FieldAccessor(classField.name)
            }
        }
    }

    is PandaValueByInstance -> {
        // TODO: handle static?
        instance.toPathOrNull()?.let {
            it + FieldAccessor(property)
        }
    }

    is PandaLoadedValue -> instance.toPathOrNull()

    else -> null
}

fun PandaValue.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}

fun PandaProject.getArgument(param: PandaMethodParameter): PandaArgument {
    return PandaArgument(param.index)
}

fun PandaProject.getArgumentsOf(method: PandaMethod): List<PandaArgument> {
    return method.parameters.map { getArgument(it) }
}
