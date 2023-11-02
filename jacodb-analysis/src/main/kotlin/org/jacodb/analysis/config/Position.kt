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

package org.jacodb.analysis.config

import org.jacodb.analysis.paths.AccessPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.configuration.AnyArgument
import org.jacodb.configuration.Argument
import org.jacodb.configuration.Position
import org.jacodb.configuration.PositionResolver
import org.jacodb.configuration.Result
import org.jacodb.configuration.This

class CallPositionResolverToAccessPath(
    val callStatement: JcInst,
) : PositionResolver<AccessPath> {
    override fun resolve(position: Position): AccessPath {
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        return when (position) {
            AnyArgument -> error("Unexpected $position")

            is Argument -> callExpr.args[position.index].toPathOrNull()
                ?: error("Cannot resolve $position for $callStatement")

            This -> (callExpr as? JcInstanceCallExpr)?.instance?.toPathOrNull()
                ?: error("Cannot resolve $position for $callStatement")

            Result -> if (callStatement is JcAssignInst) {
                callStatement.lhv.toPathOrNull()
            } else {
                callExpr.toPathOrNull()
            } ?: error("Cannot resolve $position for $callStatement")
        }
    }
}

class CallPositionResolverToJcValue(
    val callStatement: JcInst,
) : PositionResolver<JcValue> {
    override fun resolve(position: Position): JcValue {
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        return when (position) {
            AnyArgument -> error("Unexpected $position")

            is Argument -> callExpr.args[position.index]

            This -> (callExpr as? JcInstanceCallExpr)?.instance
                ?: error("Cannot resolve $position for $callStatement")

            Result -> error("Unexpected $position")
        }
    }
}

class CallPositionResolver2(
    val callExpr: JcCallExpr,
) : PositionResolver<ResolvedPosition<*>?> {
    override fun resolve(position: Position): ResolvedPosition<*>? = when (position) {
        AnyArgument -> error("Unexpected $position")

        is Argument -> {
            val value = resolveArgument(position.index)
            ResolvedJcValue(position, value)
        }

        This -> {
            resolveThis()?.let { ResolvedJcValue(position, it) }
        }

        Result -> {
            val expr = resolveResult()
            ResolvedJcExpr(position, expr)
        }
    }

    fun resolveArgument(index: Int): JcValue {
        return callExpr.args[index]
    }

    fun resolveThis(): JcValue? {
        return if (callExpr is JcInstanceCallExpr) {
            callExpr.instance
        } else {
            null
        }
    }

    fun resolveResult(): JcExpr {
        return callExpr
    }
}

sealed class ResolvedPosition<T>(
    val position: Position,
    val resolved: T,
)

class ResolvedJcValue(position: Position, resolved: JcValue) :
    ResolvedPosition<JcValue>(position, resolved)

class ResolvedJcExpr(position: Position, resolved: JcExpr) :
    ResolvedPosition<JcExpr>(position, resolved)

fun resolveArgument(position: Argument, callExpr: JcCallExpr): JcValue {
    return callExpr.args[position.index]
}

fun resolveThis(position: This, callExpr: JcInstanceCallExpr): JcValue {
    return callExpr.instance
}

fun resolveResult(position: Result, callExpr: JcCallExpr): JcExpr {
    return callExpr
}
