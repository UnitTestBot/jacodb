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

import org.jacodb.api.common.CommonTypedField

interface CommonValue : CommonExpr {
    interface Visitor<out T> {
        fun visitExternalCommonValue(value: CommonValue): T

        interface Default<out T> : Visitor<T> {
            fun defaultVisitCommonValue(value: CommonValue): T

            override fun visitExternalCommonValue(value: CommonValue): T = defaultVisitCommonValue(value)
        }
    }

    fun <T> accept(visitor: Visitor<T>): T = acceptCommonValue(visitor)
    fun <T> acceptCommonValue(visitor: Visitor<T>): T {
        return visitor.visitExternalCommonValue(this)
    }
}

interface CommonThis : CommonValue

interface CommonArgument : CommonValue {
    val index: Int
    val name: String
}

interface CommonFieldRef : CommonValue {
    val instance: CommonValue?
    val field: CommonTypedField
}

interface CommonArrayAccess : CommonValue {
    val array: CommonValue
    val index: CommonValue
}
