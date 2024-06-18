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

package org.jacodb.panda.dynamic.ark.base

import org.jacodb.api.common.cfg.CommonExpr

interface ArkEntity : CommonExpr {
    val type: ArkType

    override val typeName: String
        get() = type.typeName

    interface Visitor<out R> :
        ArkImmediate.Visitor<R>,
        ArkExpr.Visitor<R>,
        ArkRef.Visitor<R> {

        interface Default<out R> : Visitor<R>,
            ArkImmediate.Visitor.Default<R>,
            ArkExpr.Visitor.Default<R>,
            ArkRef.Visitor.Default<R> {

            override fun defaultVisit(value: ArkImmediate): R = defaultVisit(value as ArkEntity)
            override fun defaultVisit(expr: ArkExpr): R = defaultVisit(expr as ArkEntity)
            override fun defaultVisit(ref: ArkRef): R = defaultVisit(ref as ArkEntity)

            fun defaultVisit(value: ArkEntity): R
        }
    }

    fun <R> accept(visitor: Visitor<R>): R
}
