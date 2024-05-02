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

import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoProject
import org.jacodb.go.api.GoValue
import org.jacodb.go.api.LongType
import org.jacodb.analysis.ifds.AccessPath
import org.jacodb.analysis.ifds.ElementAccessor
import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.api.common.Project
import org.jacodb.api.common.cfg.*
import org.jacodb.go.api.*
import org.jacodb.taint.configuration.ConstantBooleanValue
import org.jacodb.taint.configuration.ConstantIntValue
import org.jacodb.taint.configuration.ConstantStringValue
import org.jacodb.taint.configuration.ConstantValue
import org.jacodb.analysis.util.toPathOrNull
import org.jacodb.analysis.util.toPathOrNull as _toPathOrNull
import org.jacodb.analysis.util.toPath as _toPath

data class GoArgument(
    override val index: Int,
    override val name: String,
    override val operands: List<CommonValue>,
    override val typeName: String
) : CommonArgument

interface GoTraits : Traits<GoMethod, GoInst> {

    override val GoMethod.thisInstance: CommonThis
        get() = this as CommonThis

    // TODO
    override val GoMethod.isConstructor: Boolean
        get() = false

    override fun CommonExpr.toPathOrNull(): AccessPath? {
        check(this is GoExpr)
        return _toPathOrNull()
    }

    override fun CommonValue.toPathOrNull(): AccessPath? {
        check(this is GoValue)
        return _toPathOrNull()
    }

    override fun CommonValue.toPath(): AccessPath {
        check(this is GoValue)
        return _toPath()
    }

    override val CommonCallExpr.callee: GoMethod
        get() {
            check(this is GoCallExpr)
            return callee ?: GoFunction(LongType(), emptyList(), "ERROR!!!", emptyList(), emptyList(), "")
        }

    override fun Project.getArgument(param: CommonMethodParameter): CommonArgument {
        check(this is GoProject)
        //check(param is GoParameter)
        return GoArgument(-1, "", emptyList(), "")
    }

    override fun Project.getArgumentsOf(method: GoMethod): List<CommonArgument> {
        check(this is GoProject)
        return listOf()
    }

    override fun CommonValue.isConstant(): Boolean {
        check(this is GoValue)
        return this is GoConstant
    }

    override fun CommonValue.eqConstant(constant: ConstantValue): Boolean {
        check(this is GoValue)
        return when (constant) {
            is ConstantBooleanValue -> {
                this is GoBool && this.value == constant.value
            }

            is ConstantIntValue -> {
                this is GoInt && this.value == constant.value
            }

            is ConstantStringValue -> {
                // TODO: convert to string if necessary
                this is GoStringConstant && this.value == constant.value
            }
        }
    }

    override fun CommonValue.ltConstant(constant: ConstantValue): Boolean {
        check(this is GoValue)
        return when (constant) {
            is ConstantIntValue -> {
                this is GoInt && this.value < constant.value
            }

            else -> false
        }
    }

    override fun CommonValue.gtConstant(constant: ConstantValue): Boolean {
        check(this is GoValue)
        return when (constant) {
            is ConstantIntValue -> {
                this is GoInt && this.value > constant.value
            }

            else -> false
        }
    }

    override fun CommonValue.matches(pattern: String): Boolean {
        check(this is GoValue)
        val s = this.toString()
        val re = pattern.toRegex()
        return re.matches(s)
    }

    // Ensure that all methods are default-implemented in the interface itself:
    companion object : GoTraits
}

fun GoExpr.toPathOrNull(): AccessPath? = when (this) {
    is GoValue -> toPathOrNull()
    else -> null
}

fun GoValue.toPathOrNull(): AccessPath? = when (this) {
    is GoSimpleValue -> AccessPath(this, emptyList())

    is GoSliceExpr -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    else -> null
}

fun GoValue.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}
