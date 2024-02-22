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

package org.jacodb.api.common.cfg

import org.jacodb.api.common.CommonMethod

interface CommonInst<out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    val location: CommonInstLocation<Method, Statement> // Self-type would be nice to have here...
    val operands: List<CommonExpr>

    interface Visitor<out T> {
        fun visitExternalCommonInst(inst: CommonInst<*, *>): T

        fun visitCommonAssignInst(inst: CommonAssignInst<*, *>): T
        fun visitCommonCallInst(inst: CommonCallInst<*, *>): T
        fun visitCommonReturnInst(inst: CommonReturnInst<*, *>): T
        fun visitCommonGotoInst(inst: CommonGotoInst<*, *>): T
        fun visitCommonIfInst(inst: CommonIfInst<*, *>): T

        interface Default<out T> : Visitor<T> {
            fun defaultVisitCommonInst(inst: CommonInst<*, *>): T

            override fun visitExternalCommonInst(inst: CommonInst<*, *>): T = defaultVisitCommonInst(inst)

            override fun visitCommonAssignInst(inst: CommonAssignInst<*, *>): T = defaultVisitCommonInst(inst)
            override fun visitCommonCallInst(inst: CommonCallInst<*, *>): T = defaultVisitCommonInst(inst)
            override fun visitCommonReturnInst(inst: CommonReturnInst<*, *>): T = defaultVisitCommonInst(inst)
            override fun visitCommonGotoInst(inst: CommonGotoInst<*, *>): T = defaultVisitCommonInst(inst)
            override fun visitCommonIfInst(inst: CommonIfInst<*, *>): T = defaultVisitCommonInst(inst)
        }
    }

    fun <T> accept(visitor: Visitor<T>): T = acceptCommonInst(visitor)
    fun <T> acceptCommonInst(visitor: Visitor<T>): T {
        return visitor.visitExternalCommonInst(this)
    }
}

interface CommonInstLocation<out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    val method: Method
    val index: Int
    val lineNumber: Int
}

interface CommonAssignInst<out Method, out Statement> : CommonInst<Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    val lhv: CommonValue
    val rhv: CommonExpr

    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonAssignInst(this)
    }
}

// TODO: add 'callExpr: CoreExpr' property
interface CommonCallInst<out Method, out Statement> : CommonInst<Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {
    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonCallInst(this)
    }
}

interface CommonReturnInst<out Method, out Statement> : CommonInst<Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    val returnValue: CommonValue?

    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonReturnInst(this)
    }
}

interface CommonGotoInst<out Method, out Statement> : CommonInst<Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonGotoInst(this)
    }
}

interface CommonIfInst<out Method, out Statement> : CommonInst<Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonIfInst(this)
    }
}
