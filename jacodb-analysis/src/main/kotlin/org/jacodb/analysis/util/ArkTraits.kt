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
import org.jacodb.analysis.util.toPathOrNull
import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.api.common.cfg.CommonArgument
import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.panda.dynamic.ark.base.AnyType
import org.jacodb.panda.dynamic.ark.base.ArkConstant
import org.jacodb.panda.dynamic.ark.base.ArkEntity
import org.jacodb.panda.dynamic.ark.base.ArkThis
import org.jacodb.panda.dynamic.ark.base.ArkValue
import org.jacodb.panda.dynamic.ark.base.ArrayAccess
import org.jacodb.panda.dynamic.ark.base.CallExpr
import org.jacodb.panda.dynamic.ark.base.CastExpr
import org.jacodb.panda.dynamic.ark.base.Immediate
import org.jacodb.panda.dynamic.ark.base.InstanceFieldRef
import org.jacodb.panda.dynamic.ark.base.ParameterRef
import org.jacodb.panda.dynamic.ark.base.StaticFieldRef
import org.jacodb.panda.dynamic.ark.base.Stmt
import org.jacodb.panda.dynamic.ark.model.ArkFile
import org.jacodb.panda.dynamic.ark.model.ArkMethod
import org.jacodb.panda.dynamic.ark.model.ArkMethodImpl
import org.jacodb.panda.dynamic.ark.model.ArkMethodParameter
import org.jacodb.taint.configuration.ConstantValue
import org.jacodb.analysis.util.toPath as _toPath
import org.jacodb.analysis.util.toPathOrNull as _toPathOrNull
import org.jacodb.panda.dynamic.ark.utils.getOperands as _getOperands

interface ArkTraits : Traits<ArkMethod, Stmt> {

    override val CommonCallExpr.callee: ArkMethod
        get() {
            check(this is CallExpr)
            // return cp.getMethodBySignature(method) ?: error("Method not found: $method")
            return ArkMethodImpl(method, emptyList())
        }

    override val ArkMethod.thisInstance: ArkThis
        get() = ArkThis(AnyType)

    override val ArkMethod.isConstructor: Boolean
        get() = false

    override fun CommonExpr.toPathOrNull(): AccessPath? {
        check(this is ArkEntity)
        return this._toPathOrNull()
    }

    override fun CommonValue.toPathOrNull(): AccessPath? {
        check(this is ArkEntity)
        return this._toPathOrNull()
    }

    override fun CommonValue.toPath(): AccessPath {
        check(this is ArkEntity)
        return this._toPath()
    }

    override fun getArgument(param: CommonMethodParameter): ParameterRef {
        check(param is ArkMethodParameter)
        return ParameterRef(index = param.index, type = param.type)
    }

    override fun getArgumentsOf(method: ArkMethod): List<CommonArgument> {
        return method.parameters.map { getArgument(it) }
    }

    override fun CommonValue.isConstant(): Boolean {
        check(this is ArkEntity)
        return this is ArkConstant
    }

    override fun CommonValue.eqConstant(constant: ConstantValue): Boolean {
        TODO("Not yet implemented")
    }

    override fun CommonValue.ltConstant(constant: ConstantValue): Boolean {
        TODO("Not yet implemented")
    }

    override fun CommonValue.gtConstant(constant: ConstantValue): Boolean {
        TODO("Not yet implemented")
    }

    override fun CommonValue.matches(pattern: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun Stmt.getCallExpr(): CallExpr? {
        return callExpr
    }

    override fun CommonExpr.getValues(): Set<ArkValue> {
        check(this is ArkEntity)
        return _getOperands().filterIsInstance<ArkValue>().toSet()
    }

    override fun Stmt.getOperands(): List<ArkEntity> {
        return _getOperands().toList()
    }

    companion object : ArkTraits {
        lateinit var cp: ArkFile
    }
}

fun ArkEntity.toPathOrNull(): AccessPath? = when (this) {
    is Immediate -> AccessPath(this, emptyList())

    is ArkThis -> AccessPath(this, emptyList())

    is ArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is InstanceFieldRef -> {
        instance.toPathOrNull()?.let {
            it + FieldAccessor(field.name)
        }
    }

    is StaticFieldRef -> {
        AccessPath(null, listOf(FieldAccessor(field.name, isStatic = true)))
    }

    is CastExpr -> arg.toPathOrNull()

    else -> null
}

fun ArkEntity.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}

val Stmt.callExpr: CallExpr?
    get() = _getOperands().filterIsInstance<CallExpr>().firstOrNull()
