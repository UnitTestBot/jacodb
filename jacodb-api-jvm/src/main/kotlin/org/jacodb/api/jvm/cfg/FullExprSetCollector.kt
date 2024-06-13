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

package org.jacodb.api.jvm.cfg

abstract class AbstractFullRawExprSetCollector :
    JcRawExprVisitor.Default<Any>,
    JcRawInstVisitor.Default<Any> {

    abstract fun ifMatches(expr: JcRawExpr)

    override fun defaultVisitJcRawExpr(expr: JcRawExpr) {
        ifMatches(expr)
        expr.operands.forEach {
            it.accept(this)
        }
    }

    override fun defaultVisitJcRawInst(inst: JcRawInst) {
        inst.operands.forEach {
            it.accept(this)
        }
    }
}

abstract class AbstractFullExprSetCollector :
    JcExprVisitor.Default<Any>,
    JcInstVisitor.Default<Any> {

    abstract fun ifMatches(expr: JcExpr)

    override fun defaultVisitJcExpr(expr: JcExpr) {
        ifMatches(expr)
        expr.operands.forEach {
            it.accept(this)
        }
    }

    override fun defaultVisitJcInst(inst: JcInst) {
        inst.operands.forEach {
            it.accept(this)
        }
    }
}
