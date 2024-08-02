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

package org.jacodb.ets.base

import org.jacodb.api.common.cfg.CommonValue

interface EtsValue : EtsEntity, CommonValue {
    interface Visitor<out R> {
        fun visit(value: EtsLocal): R

        // Constant
        fun visit(value: EtsStringConstant): R
        fun visit(value: EtsBooleanConstant): R
        fun visit(value: EtsNumberConstant): R
        fun visit(value: EtsNullConstant): R
        fun visit(value: EtsUndefinedConstant): R
        fun visit(value: EtsArrayLiteral): R
        fun visit(value: EtsObjectLiteral): R

        // Ref
        fun visit(value: EtsThis): R
        fun visit(value: EtsParameterRef): R
        fun visit(value: EtsArrayAccess): R
        fun visit(value: EtsInstanceFieldRef): R
        fun visit(value: EtsStaticFieldRef): R

        interface Default<out R> : Visitor<R> {
            override fun visit(value: EtsLocal): R = defaultVisit(value)

            override fun visit(value: EtsStringConstant): R = defaultVisit(value)
            override fun visit(value: EtsBooleanConstant): R = defaultVisit(value)
            override fun visit(value: EtsNumberConstant): R = defaultVisit(value)
            override fun visit(value: EtsNullConstant): R = defaultVisit(value)
            override fun visit(value: EtsUndefinedConstant): R = defaultVisit(value)
            override fun visit(value: EtsArrayLiteral): R = defaultVisit(value)
            override fun visit(value: EtsObjectLiteral): R = defaultVisit(value)

            override fun visit(value: EtsThis): R = defaultVisit(value)
            override fun visit(value: EtsParameterRef): R = defaultVisit(value)
            override fun visit(value: EtsArrayAccess): R = defaultVisit(value)
            override fun visit(value: EtsInstanceFieldRef): R = defaultVisit(value)
            override fun visit(value: EtsStaticFieldRef): R = defaultVisit(value)

            fun defaultVisit(value: EtsValue): R
        }
    }

    override fun <R> accept(visitor: EtsEntity.Visitor<R>): R {
        return accept(visitor as Visitor<R>)
    }

    fun <R> accept(visitor: Visitor<R>): R
}
