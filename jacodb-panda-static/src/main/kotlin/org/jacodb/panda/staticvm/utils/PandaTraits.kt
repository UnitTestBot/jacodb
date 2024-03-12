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

package org.jacodb.panda.staticvm.utils

import org.jacodb.analysis.ifds.AccessPath
import org.jacodb.analysis.util.Traits
import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.api.common.Project
import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.panda.staticvm.cfg.*
import org.jacodb.panda.staticvm.classpath.MethodNode
import org.jacodb.panda.staticvm.classpath.PandaClasspath
import org.jacodb.panda.staticvm.utils.PandaTraits.Companion.isConstructor

interface PandaTraits : Traits<MethodNode, PandaInst> {

    override val MethodNode.thisInstance: PandaThis
        get() = PandaThis(enclosingClass)

    // TODO
    override val MethodNode.isConstructor: Boolean
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

    override val CommonCallExpr.callee: MethodNode
        get() {
            check(this is PandaCallExpr)
            return method
        }

    override fun Project.getArgument(param: CommonMethodParameter): PandaArgument {
        check(this is PandaClasspath)
        check(param is MethodNode.Parameter)
        return _getArgument(param)
    }

    override fun Project.getArgumentsOf(method: MethodNode): List<PandaArgument> {
        check(this is PandaProject)
        return _getArgumentsOf(method)
    }

    // Ensure that all methods are default-implemented in the interface itself:
    companion object : PandaTraits
}

val MethodNode._thisInstance: PandaThis
    get() = PandaThis(enclosingClass.toType())

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

fun PandaProject.getArgument(param: MethodNodeParameter): PandaArgument {
    return PandaArgument(param.index)
}

fun PandaProject.getArgumentsOf(method: MethodNode): List<PandaArgument> {
    return method.parameters.map { getArgument(it) }
}