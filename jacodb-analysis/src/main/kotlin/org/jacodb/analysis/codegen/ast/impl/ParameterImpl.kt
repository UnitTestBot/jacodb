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

class ParameterImpl(
    override val usage: TypeUsage,
    override val shortName: String,
    // invariant - two parameters relates to the same function if they point to the same function
    // currently we prohibit shadowing local variables,
    // it means that local variables and parameters can be identified by its name and parent function
    override val parentCallable: org.jacodb.analysis.codegen.ast.base.CallablePresentation,
    // just for correct code generation
    override val indexInSignature: Int
) : FunctionLocalImpl(), ParameterPresentation {
    override fun equals(other: Any?): Boolean {
        assert(
            // here we check that if this is 2 different parameters that refers to the same function --
            this === other
                    || other !is ParameterPresentation
                    || parentCallable != other.parentCallable
                    // -- they stay on different indices
                    || indexInSignature != other.indexInSignature
        )
        // and here we check their names
        return super.equals(other)
    }
}