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

package org.jacodb.analysis.codegen.ast.impl

import org.jacodb.analysis.codegen.ast.base.*

abstract class FunctionLocalImpl : CallableLocal {
    override val reference: ValueReference by lazy { SimpleValueReference(this) }

    override fun hashCode(): Int {
        return parentCallable.hashCode() * 31 + shortName.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is CallableLocal || parentCallable != other.parentCallable) {
            // there are no constraints on locals that refer to different callables
            return false
        }

        if (other !== this) {
            // locals in the same callable should have unique names
            assert(shortName != other.shortName)
        }

        return this === other
    }

    override val fqnName: String
        get() = (parentCallable as? NameOwner)?.fqnName?.let { "$it." } + shortName
}