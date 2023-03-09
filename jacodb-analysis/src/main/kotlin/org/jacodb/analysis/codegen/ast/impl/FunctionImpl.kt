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

open class FunctionImpl(
    graphId: Int,
    override val shortName: String = "functionFor$graphId",
    override val visibility: VisibilityModifier = VisibilityModifier.PUBLIC,
    returnType: TypeUsage = TypePresentation.voidType.instanceType,
    parameters: List<Pair<TypeUsage, String>> = emptyList()
) : CallableImpl(graphId, returnType, parameters), FunctionPresentation {
    override fun equals(other: Any?): Boolean {
        if (other !is FunctionImpl)
            return false

        if (graphId == other.graphId) {
            assert(this === other)
        } else {
            // all functions(including methods) should have unique <fqn, signatures>
            // and so 2 functions should not have same fqn and signature simultaneously
            // todo or one should override another if this is method
            assert(fqnName != other.fqnName || signature != other.signature)
        }

        return this === other
    }
}