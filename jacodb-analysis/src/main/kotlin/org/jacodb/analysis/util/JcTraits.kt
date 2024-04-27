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
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcParameter
import org.jacodb.api.jvm.cfg.JcArgument
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcCallExpr
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcConstant
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcSimpleValue
import org.jacodb.api.jvm.cfg.JcStringConstant
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.toType
import org.jacodb.taint.configuration.ConstantBooleanValue
import org.jacodb.taint.configuration.ConstantIntValue
import org.jacodb.taint.configuration.ConstantStringValue
import org.jacodb.taint.configuration.ConstantValue
import org.jacodb.analysis.util.callee as _callee
import org.jacodb.analysis.util.getArgument as _getArgument
import org.jacodb.analysis.util.getArgumentsOf as _getArgumentsOf
import org.jacodb.analysis.util.thisInstance as _thisInstance
import org.jacodb.analysis.util.toPath as _toPath
import org.jacodb.analysis.util.toPathOrNull as _toPathOrNull

/**
 * JVM-specific extensions for analysis.
 *
 * ### Usage:
 * ```
 * class MyClass {
 *     companion object : JcTraits
 * }
 * ```
 */
interface JcTraits : Traits<JcClasspath, JcMethod, JcInst, JcValue, JcExpr, JcCallExpr, JcParameter> {

    // Ensure that all methods are default-implemented in the interface itself:
    companion object : JcTraits

    override val JcMethod.thisInstance: JcThis
        get() = this._thisInstance

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override val JcMethod.isConstructor: Boolean
        get() = this.isConstructor

    override fun JcExpr.toPathOrNull(): AccessPath? {
        return this._toPathOrNull()
    }

    override fun JcValue.toPathOrNull(): AccessPath? {
        return this._toPathOrNull()
    }

    override fun JcValue.toPath(): AccessPath {
        return this._toPath()
    }

    override val JcCallExpr.callee: JcMethod
        get() = this._callee

    override fun getArgument(project: JcClasspath, param: JcParameter): JcArgument? {
        return project._getArgument(param)
    }

    override fun getArguments(project: JcClasspath, method: JcMethod): List<JcArgument> {
        return project._getArgumentsOf(method)
    }

    override fun JcValue.isConstant(): Boolean {
        return this is JcConstant
    }

    override fun JcValue.eqConstant(constant: ConstantValue): Boolean {
        return when (constant) {
            is ConstantBooleanValue -> {
                this is JcBool && value == constant.value
            }

            is ConstantIntValue -> {
                this is JcInt && value == constant.value
            }

            is ConstantStringValue -> {
                // TODO: if 'value' is not string, convert it to string and compare with 'constant.value'
                this is JcStringConstant && value == constant.value
            }
        }
    }

    override fun JcValue.ltConstant(constant: ConstantValue): Boolean {
        return when (constant) {
            is ConstantIntValue -> {
                this is JcInt && value < constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun JcValue.gtConstant(constant: ConstantValue): Boolean {
        return when (constant) {
            is ConstantIntValue -> {
                this is JcInt && value > constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun JcValue.matches(pattern: String): Boolean {
        val s = toString()
        val re = pattern.toRegex()
        return re.matches(s)
    }
}

val JcMethod.thisInstance: JcThis
    get() = JcThis(enclosingClass.toType())

val JcCallExpr.callee: JcMethod
    get() = method.method

fun JcExpr.toPathOrNull(): AccessPath? = when (this) {
    is JcValue -> toPathOrNull()
    is JcCastExpr -> operand.toPathOrNull()
    else -> null
}

fun JcValue.toPathOrNull(): AccessPath? = when (this) {
    is JcSimpleValue -> AccessPath(this, emptyList())

    is JcArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is JcFieldRef -> {
        val instance = instance
        if (instance == null) {
            require(field.isStatic) { "Expected static field" }
            AccessPath(null, listOf(FieldAccessor(field.field)))
        } else {
            instance.toPathOrNull()?.let {
                it + FieldAccessor(field.field)
            }
        }
    }

    else -> null
}

fun JcValue.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}

fun JcClasspath.getArgument(param: JcParameter): JcArgument? {
    val t = findTypeOrNull(param.type.typeName) ?: return null
    return JcArgument.of(param.index, param.name, t)
}

fun JcClasspath.getArgumentsOf(method: JcMethod): List<JcArgument> {
    return method.parameters.map { getArgument(it)!! }
}
