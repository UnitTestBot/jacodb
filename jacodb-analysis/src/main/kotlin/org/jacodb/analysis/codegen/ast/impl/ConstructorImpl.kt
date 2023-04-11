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
import org.jacodb.analysis.codegen.ast.base.expression.invocation.ObjectCreationExpression
import org.jacodb.analysis.codegen.ast.base.presentation.type.ConstructorPresentation
import org.jacodb.analysis.codegen.ast.base.presentation.type.TypePresentation
import org.jacodb.analysis.codegen.ast.base.typeUsage.TypeUsage

class ConstructorImpl(
    graphId: Int,
    override val containingType: TypePresentation,
    override val visibility: VisibilityModifier,
    override val parentConstructorCall: ObjectCreationExpression?,
    parameters: List<Pair<TypeUsage, String>>,
    override var comments: ArrayList<String> = ArrayList()
) : CallableImpl(graphId, containingType.instanceType, parameters), ConstructorPresentation {
    override val returnType: TypeUsage
        get() = super<CallableImpl>.returnType

    override fun equals(other: Any?): Boolean {
        if (other !is ConstructorImpl) {
            return false
        }

        if (other.containingType != containingType) {
            return false
        }

        if (!super.equals(other)) {
            // we prohibit different constructors with the same signature
            assert(signature != other.signature)
            return false
        }

        return true
    }
}