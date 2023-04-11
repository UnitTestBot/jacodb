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
import org.jacodb.analysis.codegen.ast.base.presentation.type.MethodPresentation
import org.jacodb.analysis.codegen.ast.base.presentation.type.TypePresentation
import org.jacodb.analysis.codegen.ast.base.typeUsage.InstanceTypeUsage
import org.jacodb.analysis.codegen.ast.base.typeUsage.TypeUsage

class StaticCounterPartTypeImpl(typeImpl: TypeImpl, override var comments: ArrayList<String> = ArrayList()) : TypeImpl(typeImpl.shortName), TypePresentation {
    override val staticCounterPart: TypePresentation = typeImpl

    override fun overrideMethod(methodToOverride: MethodPresentation): MethodPresentation {
        throw IllegalStateException("Methods in static counterparts cannot be overridden")
    }

    override fun createConstructor(
        graphId: Int,
        visibility: VisibilityModifier,
        parentConstructorCall: ObjectCreationExpression?,
        parameters: List<Pair<TypeUsage, String>>
    ): ConstructorPresentation {
        throw IllegalStateException("Constructors in static counterparts cannot be created")
    }

    override val defaultValue
        get() = throw IllegalStateException("static types dont have default value")

    override val defaultConstructor: ConstructorPresentation
        get() = throw IllegalStateException("static types cannot be instantiated")

    override val instanceType: InstanceTypeUsage
        get() = throw IllegalStateException("static types cannot be referenced")
}