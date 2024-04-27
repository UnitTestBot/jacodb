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
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaMethodParameter
import org.jacodb.panda.dynamic.api.PandaNumberConstant
import org.jacodb.panda.dynamic.api.PandaProject
import org.jacodb.panda.dynamic.api.PandaSimpleValue
import org.jacodb.panda.dynamic.api.PandaStringConstant
import org.jacodb.panda.dynamic.api.PandaThis
import org.jacodb.panda.dynamic.api.PandaValue
import org.jacodb.taint.configuration.ConstantBooleanValue
import org.jacodb.taint.configuration.ConstantIntValue
import org.jacodb.taint.configuration.ConstantStringValue
import org.jacodb.taint.configuration.ConstantValue
import org.jacodb.analysis.util.getArgument as _getArgument
import org.jacodb.analysis.util.getArgumentsOf as _getArgumentsOf
import org.jacodb.analysis.util.thisInstance as _thisInstance
import org.jacodb.analysis.util.toPath as _toPath
import org.jacodb.analysis.util.toPathOrNull as _toPathOrNull

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
interface PandaTraits :
    Traits<PandaProject, PandaMethod, PandaInst, PandaValue, PandaExpr, PandaCallExpr, PandaMethodParameter> {

    // Ensure that all methods are default-implemented in the interface itself:
    companion object : PandaTraits

    override val PandaMethod.thisInstance: PandaThis
        get() = _thisInstance

    // TODO
    override val PandaMethod.isConstructor: Boolean
        get() = false

    override fun PandaExpr.toPathOrNull(): AccessPath? {
        return _toPathOrNull()
    }

    override fun PandaValue.toPathOrNull(): AccessPath? {
        return _toPathOrNull()
    }

    override fun PandaValue.toPath(): AccessPath {
        return _toPath()
    }

    override val PandaCallExpr.callee: PandaMethod
        get() = method

    override fun getArgument(project: PandaProject, param: PandaMethodParameter): PandaArgument {
        return project._getArgument(param)
    }

    override fun getArguments(project: PandaProject, method: PandaMethod): List<PandaArgument> {
        return project._getArgumentsOf(method)
    }

    override fun PandaValue.isConstant(): Boolean {
        return this is PandaConstant
    }

    override fun PandaValue.eqConstant(constant: ConstantValue): Boolean {
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

    override fun PandaValue.ltConstant(constant: ConstantValue): Boolean {
        return when (constant) {
            is ConstantIntValue -> {
                this is PandaNumberConstant && this.value < constant.value
            }

            else -> false
        }
    }

    override fun PandaValue.gtConstant(constant: ConstantValue): Boolean {
        return when (constant) {
            is ConstantIntValue -> {
                this is PandaNumberConstant && this.value > constant.value
            }

            else -> false
        }
    }

    override fun PandaValue.matches(pattern: String): Boolean {
        val s = this.toString()
        val re = pattern.toRegex()
        return re.matches(s)
    }
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
            AccessPath(null, listOf(FieldAccessor(classField)))
        } else {
            instance.toPathOrNull()?.let {
                it + FieldAccessor(classField)
            }
        }
    }

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
