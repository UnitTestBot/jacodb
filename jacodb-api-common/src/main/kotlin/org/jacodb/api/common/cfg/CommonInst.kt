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

interface CommonInst {
    val location: CommonInstLocation
    val operands: List<CommonExpr>

    val method: CommonMethod
        get() = location.method
    val lineNumber: Int
        get() = location.lineNumber

    interface Visitor<out T> {
        fun visitExternalCommonInst(inst: CommonInst): T

        fun visitCommonAssignInst(inst: CommonAssignInst): T
        fun visitCommonCallInst(inst: CommonCallInst): T
        fun visitCommonReturnInst(inst: CommonReturnInst): T
        fun visitCommonGotoInst(inst: CommonGotoInst): T
        fun visitCommonIfInst(inst: CommonIfInst): T

        interface Default<out T> : Visitor<T> {
            fun defaultVisitCommonInst(inst: CommonInst): T

            override fun visitExternalCommonInst(inst: CommonInst): T = defaultVisitCommonInst(inst)

            override fun visitCommonAssignInst(inst: CommonAssignInst): T = defaultVisitCommonInst(inst)
            override fun visitCommonCallInst(inst: CommonCallInst): T = defaultVisitCommonInst(inst)
            override fun visitCommonReturnInst(inst: CommonReturnInst): T = defaultVisitCommonInst(inst)
            override fun visitCommonGotoInst(inst: CommonGotoInst): T = defaultVisitCommonInst(inst)
            override fun visitCommonIfInst(inst: CommonIfInst): T = defaultVisitCommonInst(inst)
        }
    }

    fun <T> accept(visitor: Visitor<T>): T = acceptCommonInst(visitor)
    fun <T> acceptCommonInst(visitor: Visitor<T>): T {
        return visitor.visitExternalCommonInst(this)
    }
}

interface CommonInstLocation {
    val method: CommonMethod
    val index: Int
    val lineNumber: Int
}

interface CommonAssignInst : CommonInst {
    val lhv: CommonValue
    val rhv: CommonExpr

    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonAssignInst(this)
    }
}

// TODO: add 'callExpr: CoreExpr' property
interface CommonCallInst : CommonInst {
    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonCallInst(this)
    }
}

interface CommonReturnInst : CommonInst {
    val returnValue: CommonValue?

    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonReturnInst(this)
    }
}

interface CommonGotoInst : CommonInst {
    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonGotoInst(this)
    }
}

interface CommonIfInst : CommonInst {
    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonIfInst(this)
    }
}
