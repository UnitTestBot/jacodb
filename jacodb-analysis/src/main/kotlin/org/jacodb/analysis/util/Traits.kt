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

import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.ext.toType
import org.jacodb.panda.dynamic.api.PandaClass
import org.jacodb.panda.dynamic.api.PandaClassType
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaThis

/**
 * Extensions for analysis.
 */
interface Traits<out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    fun thisInstance(method: @UnsafeVariance Method): CommonThis
    fun isConstructor(method: @UnsafeVariance Method): Boolean
}

// JVM
object JcTraits : Traits<JcMethod, JcInst> {
    override fun thisInstance(method: JcMethod): JcThis {
        return JcThis(method.enclosingClass.toType())
    }

    override fun isConstructor(method: JcMethod): Boolean {
        return method.isConstructor
    }
}

// Panda
object PandaTraits : Traits<PandaMethod, PandaInst> {
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
}
