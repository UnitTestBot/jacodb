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
import org.jacodb.analysis.ifds.Edge
import org.jacodb.analysis.ifds.ElementAccessor
import org.jacodb.analysis.ifds.Runner
import org.jacodb.analysis.ifds.UniRunner
import org.jacodb.analysis.taint.TaintBidiRunner
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.api.common.Project
import org.jacodb.api.common.cfg.CommonArgument
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcParameter
import org.jacodb.api.jvm.cfg.JcArgument
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.ext.toType

fun Project.getArgument(param: CommonMethodParameter): CommonArgument? {
    return when {
        this is JcClasspath && param is JcParameter -> getArgument(param)
        else -> error("Cannot get argument from parameter: $param")
    }
}

fun Project.getArgumentsOf(method: CommonMethod<*, *>): List<CommonArgument> {
    return when {
        this is JcClasspath && method is JcMethod -> getArgumentsOf(method)
        else -> error("Cannot get arguments of method: $method")
    }
}

fun JcClasspath.getArgument(param: JcParameter): JcArgument? {
    val t = findTypeOrNull(param.type.typeName) ?: return null
    return JcArgument.of(param.index, param.name, t)
}

fun JcClasspath.getArgumentsOf(method: JcMethod): List<JcArgument> {
    return method.parameters.map { getArgument(it)!! }
}

fun AccessPath?.startsWith(other: AccessPath?): Boolean {
    if (this == null || other == null) {
        return false
    }
    if (this.value != other.value) {
        return false
    }
    return this.accesses.take(other.accesses.size) == other.accesses
}

internal fun AccessPath.removeTrailingElementAccessors(): AccessPath {
    var index = accesses.size
    while (index > 0 && accesses[index - 1] is ElementAccessor) {
        index--
    }
    return AccessPath(value, accesses.subList(0, index))
}

internal fun Runner<*, *, *>.getPathEdges(): Set<Edge<*, *, *>> = when (this) {
    is UniRunner<*, *, *, *> -> pathEdges
    is TaintBidiRunner<*, *> -> forwardRunner.getPathEdges() + backwardRunner.getPathEdges()
    else -> error("Cannot extract pathEdges for $this")
}

abstract class AbstractFullCommonExprSetCollector :
    CommonExpr.Visitor.Default<Any>,
    CommonInst.Visitor.Default<Any> {

    abstract fun ifMatches(expr: CommonExpr)

    override fun defaultVisitCommonExpr(expr: CommonExpr) {
        ifMatches(expr)
        expr.operands.forEach { it.accept(this) }
    }

    override fun defaultVisitCommonInst(inst: CommonInst<*, *>) {
        inst.operands.forEach { it.accept(this) }
    }
}

abstract class TypedCommonExprResolver<T : CommonExpr> : AbstractFullCommonExprSetCollector() {
    val result: MutableSet<T> = hashSetOf()
}

class CommonValueResolver : TypedCommonExprResolver<CommonValue>() {
    override fun ifMatches(expr: CommonExpr) {
        if (expr is CommonValue) {
            result.add(expr)
        }
    }
}

val CommonExpr.values: Set<CommonValue>
    get() {
        val resolver = CommonValueResolver()
        accept(resolver)
        return resolver.result
    }

val CommonInst<*, *>.values: Set<CommonValue>
    get() {
        val resolver = CommonValueResolver()
        accept(resolver)
        return resolver.result
    }
