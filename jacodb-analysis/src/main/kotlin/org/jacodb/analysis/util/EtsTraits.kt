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
import org.jacodb.panda.dynamic.ets.base.EtsAnyType
import org.jacodb.panda.dynamic.ets.base.EtsArrayAccess
import org.jacodb.panda.dynamic.ets.base.EtsCallExpr
import org.jacodb.panda.dynamic.ets.base.EtsCastExpr
import org.jacodb.panda.dynamic.ets.base.EtsConstant
import org.jacodb.panda.dynamic.ets.base.EtsEntity
import org.jacodb.panda.dynamic.ets.base.EtsImmediate
import org.jacodb.panda.dynamic.ets.base.EtsInstanceFieldRef
import org.jacodb.panda.dynamic.ets.base.EtsParameterRef
import org.jacodb.panda.dynamic.ets.base.EtsStaticFieldRef
import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.base.EtsThis
import org.jacodb.panda.dynamic.ets.base.EtsValue
import org.jacodb.panda.dynamic.ets.model.EtsMethod
import org.jacodb.panda.dynamic.ets.model.EtsMethodImpl
import org.jacodb.panda.dynamic.ets.model.EtsMethodParameter
import org.jacodb.panda.dynamic.ets.utils.callExpr
import org.jacodb.taint.configuration.ConstantValue
import org.jacodb.analysis.util.toPath as _toPath
import org.jacodb.analysis.util.toPathOrNull as _toPathOrNull
import org.jacodb.panda.dynamic.ets.utils.getOperands as _getOperands
import org.jacodb.panda.dynamic.ets.utils.getValues as _getValues

interface EtsTraits : Traits<EtsMethod, EtsStmt> {

    override val CommonCallExpr.callee: EtsMethod
        get() {
            check(this is EtsCallExpr)
            // TODO: here, we should use the classpath to resolve the method by its signature, like so:
            //       `return cp.getMethodBySignature(method) ?: error("Method not found: $method")`
            //       However, currently EtsFile (classpath) is not able to perform method lookup by signature,
            //       and even is not available in this context.
            //       So, we just construct a new method instance with necessary signature, but without a CFG, for now.
            return EtsMethodImpl(method)
        }

    override val EtsMethod.thisInstance: EtsThis
        get() = EtsThis(EtsAnyType)

    override val EtsMethod.isConstructor: Boolean
        get() = name == "constructor"

    override fun CommonExpr.toPathOrNull(): AccessPath? {
        check(this is EtsEntity)
        return this._toPathOrNull()
    }

    override fun CommonValue.toPathOrNull(): AccessPath? {
        check(this is EtsValue)
        return this._toPathOrNull()
    }

    override fun CommonValue.toPath(): AccessPath {
        check(this is EtsValue)
        return this._toPath()
    }

    override fun getArgument(param: CommonMethodParameter): EtsParameterRef {
        check(param is EtsMethodParameter)
        return EtsParameterRef(index = param.index, type = param.type)
    }

    override fun getArgumentsOf(method: EtsMethod): List<CommonArgument> {
        return method.parameters.map { getArgument(it) }
    }

    override fun CommonValue.isConstant(): Boolean {
        check(this is EtsEntity)
        return this is EtsConstant
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

    override fun EtsStmt.getCallExpr(): EtsCallExpr? {
        return callExpr
    }

    override fun CommonExpr.getValues(): Set<EtsValue> {
        check(this is EtsEntity)
        return _getValues().toSet()
    }

    override fun EtsStmt.getOperands(): List<EtsEntity> {
        return _getOperands().toList()
    }

    companion object : EtsTraits {
        // Note: unused for now
        // lateinit var cp: EtsFile
    }
}

fun EtsEntity.toPathOrNull(): AccessPath? = when (this) {
    is EtsImmediate -> AccessPath(this, emptyList())

    is EtsThis -> AccessPath(this, emptyList())

    is EtsParameterRef -> AccessPath(this, emptyList())

    is EtsArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is EtsInstanceFieldRef -> {
        instance.toPathOrNull()?.let {
            it + FieldAccessor(field.name)
        }
    }

    is EtsStaticFieldRef -> {
        AccessPath(null, listOf(FieldAccessor(field.name, isStatic = true)))
    }

    is EtsCastExpr -> arg.toPathOrNull()

    else -> null
}

fun EtsEntity.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}
