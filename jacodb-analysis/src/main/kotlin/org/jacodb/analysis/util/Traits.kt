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
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcSimpleValue
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.toType
import org.jacodb.panda.dynamic.api.PandaArrayAccess
import org.jacodb.panda.dynamic.api.PandaCastExpr
import org.jacodb.panda.dynamic.api.PandaClass
import org.jacodb.panda.dynamic.api.PandaClassType
import org.jacodb.panda.dynamic.api.PandaExpr
import org.jacodb.panda.dynamic.api.PandaFieldRef
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaSimpleValue
import org.jacodb.panda.dynamic.api.PandaThis
import org.jacodb.panda.dynamic.api.PandaValue

/**
 * Extensions for analysis.
 */
interface Traits<out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    val @UnsafeVariance Method.thisInstance: CommonThis
    val @UnsafeVariance Method.isConstructor: Boolean

    fun CommonExpr.toPathOrNull(): AccessPath?
    fun CommonValue.toPathOrNull(): AccessPath?
    fun CommonValue.toPath(): AccessPath

}

// JVM
object JcTraits : Traits<JcMethod, JcInst> {

    override val JcMethod.thisInstance: JcThis
        get() = JcThis(enclosingClass.toType())

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override val JcMethod.isConstructor: Boolean
        get() = isConstructor

    override fun CommonExpr.toPathOrNull(): AccessPath? {
        check(this is JcExpr)
        return toPathOrNull()
    }

    fun JcExpr.toPathOrNull(): AccessPath? = when (this) {
        is JcValue -> toPathOrNull()
        is JcCastExpr -> operand.toPathOrNull()
        else -> null
    }

    override fun CommonValue.toPathOrNull(): AccessPath? {
        check(this is JcValue)
        return toPathOrNull()
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

    override fun CommonValue.toPath(): AccessPath {
        check(this is JcValue)
        return toPath()
    }

    fun JcValue.toPath(): AccessPath {
        return toPathOrNull() ?: error("Unable to build access path for value $this")
    }
}

// Panda
object PandaTraits : Traits<PandaMethod, PandaInst> {

    override val PandaMethod.thisInstance: PandaThis
        get() = PandaThis(enclosingClass.toType())

    override val PandaMethod.isConstructor: Boolean
        // TODO
        get() = false

    private fun PandaClass.toType(): PandaClassType {
        TODO()
        // return project.classTypeOf(this)
    }

    override fun CommonExpr.toPathOrNull(): AccessPath? {
        check(this is PandaExpr)
        return toPathOrNull()
    }

    fun PandaExpr.toPathOrNull(): AccessPath? = when (this) {
        is PandaValue -> toPathOrNull()
        is PandaCastExpr -> operand.toPathOrNull()
        else -> null
    }

    override fun CommonValue.toPathOrNull(): AccessPath? {
        check(this is PandaValue)
        return toPathOrNull()
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

    override fun CommonValue.toPath(): AccessPath {
        check(this is PandaValue)
        return toPath()
    }

    fun PandaValue.toPath(): AccessPath {
        return toPathOrNull() ?: error("Unable to build access path for value $this")
    }
}
