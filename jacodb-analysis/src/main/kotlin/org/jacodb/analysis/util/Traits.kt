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
import org.jacodb.analysis.ifds.toPath
import org.jacodb.analysis.ifds.toPathOrNull
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.toType
import org.jacodb.panda.dynamic.api.PandaClass
import org.jacodb.panda.dynamic.api.PandaClassType
import org.jacodb.panda.dynamic.api.PandaExpr
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaThis
import org.jacodb.panda.dynamic.api.PandaValue

/**
 * Extensions for analysis.
 */
abstract class Traits<out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    abstract fun thisInstance(method: @UnsafeVariance Method): CommonThis
    abstract fun isConstructor(method: @UnsafeVariance Method): Boolean

    abstract fun toPathOrNull(expr: CommonExpr): AccessPath?
    abstract fun toPath(value: CommonValue): AccessPath

    val scope: Scope = Scope()

    inner class Scope {
        val @UnsafeVariance Method.thisInstance: CommonThis
            get() = thisInstance(this)

        val @UnsafeVariance Method.isConstructor: Boolean
            get() = isConstructor(this)

        fun CommonExpr.toPathOrNull(): AccessPath? = toPathOrNull(this)

        fun CommonValue.toPath(): AccessPath = toPath(this)
    }
}

// JVM
object JcTraits : Traits<JcMethod, JcInst>() {
    override fun thisInstance(method: JcMethod): JcThis {
        return JcThis(method.enclosingClass.toType())
    }

    override fun isConstructor(method: JcMethod): Boolean {
        return method.isConstructor
    }

    override fun toPathOrNull(expr: CommonExpr): AccessPath? {
        check(expr is JcExpr)
        return expr.toPathOrNull()
    }

    override fun toPath(value: CommonValue): AccessPath {
        check(value is JcValue)
        return value.toPath()
    }
}

// Panda
object PandaTraits : Traits<PandaMethod, PandaInst>() {
    override fun thisInstance(method: PandaMethod): CommonThis {
        return PandaThis(method.enclosingClass.toType())
    }

    override fun isConstructor(method: PandaMethod): Boolean {
        // TODO
        return false
    }

    private fun PandaClass.toType(): PandaClassType {
        TODO()
        // return project.classTypeOf(this)
    }

    override fun toPathOrNull(expr: CommonExpr): AccessPath? {
        check(expr is PandaExpr)
        return expr.toPathOrNull()
    }

    override fun toPath(value: CommonValue): AccessPath {
        check(value is PandaValue)
        return value.toPath()
    }
}
