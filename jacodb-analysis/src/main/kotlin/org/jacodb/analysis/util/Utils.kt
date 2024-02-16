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
import org.jacodb.analysis.ifds.Runner
import org.jacodb.analysis.ifds.UniRunner
import org.jacodb.analysis.taint.BidiRunner
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.JcParameter
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.ext.toType

val JcMethod.thisInstance: JcThis
    get() = JcThis(enclosingClass.toType())

fun JcClasspath.getArgument(param: JcParameter): JcArgument? {
    val t = findTypeOrNull(param.type.typeName) ?: return null
    return JcArgument.of(param.index, param.name, t)
}

fun JcClasspath.getArgumentsOf(method: JcMethod): List<JcArgument> {
    return method.parameters.map { getArgument(it)!! }
}

fun Runner<*>.getGetPathEdges(): Set<Edge<*>> = when (this) {
    is UniRunner<*, *> -> pathEdges
    is BidiRunner -> forwardRunner.getGetPathEdges() + backwardRunner.getGetPathEdges()
    else -> error("Cannot extract pathEdges for $this")
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
