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

import org.jacodb.analysis.ifds.CommonAccessPath
import org.jacodb.analysis.ifds.ElementAccessor
import org.jacodb.analysis.ifds.Maybe
import org.jacodb.analysis.ifds.fmap
import org.jacodb.analysis.ifds.toMaybe
import org.jacodb.analysis.ifds.toPathOrNull
import org.jacodb.analysis.util.Traits
import org.jacodb.analysis.util.getArgument
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.Project
import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonInstanceCallExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.api.common.ext.callExpr
import org.jacodb.taint.configuration.AnyArgument
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.Position
import org.jacodb.taint.configuration.PositionResolver
import org.jacodb.taint.configuration.Result
import org.jacodb.taint.configuration.ResultAnyElement
import org.jacodb.taint.configuration.This

class CallPositionToAccessPathResolver(
    private val callStatement: CommonInst<*, *>,
) : PositionResolver<Maybe<CommonAccessPath>> {
    private val callExpr = callStatement.callExpr
        ?: error("Call statement should have non-null callExpr")

    override fun resolve(position: Position): Maybe<CommonAccessPath> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> callExpr.args[position.index].toPathOrNull().toMaybe()
        This -> (callExpr as? CommonInstanceCallExpr)?.instance?.toPathOrNull().toMaybe()
        Result -> (callStatement as? CommonAssignInst)?.lhv?.toPathOrNull().toMaybe()
        ResultAnyElement -> (callStatement as? CommonAssignInst)?.lhv?.toPathOrNull().toMaybe()
            .fmap { it + ElementAccessor }
    }
}

class CallPositionToValueResolver(
    private val callStatement: CommonInst<*, *>,
) : PositionResolver<Maybe<CommonValue>> {
    private val callExpr = callStatement.callExpr
        ?: error("Call statement should have non-null callExpr")

    override fun resolve(position: Position): Maybe<CommonValue> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> Maybe.some(callExpr.args[position.index])
        This -> (callExpr as? CommonInstanceCallExpr)?.instance.toMaybe()
        Result -> (callStatement as? CommonAssignInst)?.lhv.toMaybe()
        ResultAnyElement -> Maybe.none()
    }
}

class EntryPointPositionToValueResolver(
    val method: CommonMethod<*, *>,
    val cp: Project,
    val traits: Traits<*, *>,
) : PositionResolver<Maybe<CommonValue>> {
    override fun resolve(position: Position): Maybe<CommonValue> = when (position) {
        This -> Maybe.some(traits.thisInstance(method))

        is Argument -> {
            val p = method.parameters[position.index]
            cp.getArgument(p).toMaybe()
        }

        AnyArgument, Result, ResultAnyElement -> error("Unexpected $position")
    }
}

class EntryPointPositionToAccessPathResolver(
    val method: CommonMethod<*, *>,
    val cp: Project,
    val traits: Traits<*, *>,
) : PositionResolver<Maybe<CommonAccessPath>> {
    override fun resolve(position: Position): Maybe<CommonAccessPath> = when (position) {
        This -> traits.thisInstance(method).toPathOrNull().toMaybe()

        is Argument -> {
            val p = method.parameters[position.index]
            cp.getArgument(p)?.toPathOrNull().toMaybe()
        }

        AnyArgument, Result, ResultAnyElement -> error("Unexpected $position")
    }
}
