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

package org.jacodb.analysis.ifds.config

import org.jacodb.analysis.ifds.util.AccessPath
import org.jacodb.analysis.ifds.util.ElementAccessor
import org.jacodb.analysis.ifds.util.Maybe
import org.jacodb.analysis.ifds.util.fmap
import org.jacodb.analysis.ifds.util.getArgument
import org.jacodb.analysis.ifds.util.thisInstance
import org.jacodb.analysis.ifds.util.toMaybe
import org.jacodb.analysis.ifds.util.toPathOrNull
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.taint.configuration.AnyArgument
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.Position
import org.jacodb.taint.configuration.PositionResolver
import org.jacodb.taint.configuration.Result
import org.jacodb.taint.configuration.ResultAnyElement
import org.jacodb.taint.configuration.This

class CallPositionToAccessPathResolver(
    private val callStatement: JcInst,
) : PositionResolver<Maybe<AccessPath>> {
    private val callExpr = callStatement.callExpr
        ?: error("Call statement should have non-null callExpr")

    override fun resolve(position: Position): Maybe<AccessPath> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> callExpr.args[position.index].toPathOrNull().toMaybe()
        This -> (callExpr as? JcInstanceCallExpr)?.instance?.toPathOrNull().toMaybe()
        Result -> (callStatement as? JcAssignInst)?.lhv?.toPathOrNull().toMaybe()
        ResultAnyElement -> (callStatement as? JcAssignInst)?.lhv?.toPathOrNull().toMaybe()
            .fmap { it / ElementAccessor }
    }
}

class CallPositionToJcValueResolver(
    private val callStatement: JcInst,
) : PositionResolver<Maybe<JcValue>> {
    private val callExpr = callStatement.callExpr
        ?: error("Call statement should have non-null callExpr")

    override fun resolve(position: Position): Maybe<JcValue> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> Maybe.some(callExpr.args[position.index])
        This -> (callExpr as? JcInstanceCallExpr)?.instance.toMaybe()
        Result -> (callStatement as? JcAssignInst)?.lhv.toMaybe()
        ResultAnyElement -> Maybe.none()
    }
}

class EntryPointPositionToJcValueResolver(
    val cp: JcClasspath,
    val method: JcMethod,
) : PositionResolver<Maybe<JcValue>> {
    override fun resolve(position: Position): Maybe<JcValue> = when (position) {
        This -> Maybe.some(method.thisInstance)

        is Argument -> {
            val p = method.parameters[position.index]
            cp.getArgument(p).toMaybe()
        }

        AnyArgument, Result, ResultAnyElement -> error("Unexpected $position")
    }
}

class EntryPointPositionToAccessPathResolver(
    val cp: JcClasspath,
    val method: JcMethod,
) : PositionResolver<Maybe<AccessPath>> {
    override fun resolve(position: Position): Maybe<AccessPath> = when (position) {
        This -> method.thisInstance.toPathOrNull().toMaybe()

        is Argument -> {
            val p = method.parameters[position.index]
            cp.getArgument(p)?.toPathOrNull().toMaybe()
        }

        AnyArgument, Result, ResultAnyElement -> error("Unexpected $position")
    }
}
