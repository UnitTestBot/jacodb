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
import org.jacodb.analysis.codegen.ast.base.presentation.type.*
import org.jacodb.analysis.codegen.ast.base.typeUsage.InstanceTypeUsage
import org.jacodb.analysis.codegen.ast.base.typeUsage.TypeUsage
import org.jacodb.analysis.codegen.impossibleGraphId

open class TypeImpl(
    final override val shortName: String,
    defaultConstructorGraphId: Int = impossibleGraphId,
    final override val visibility: VisibilityModifier = VisibilityModifier.PUBLIC,
    final override val inheritanceModifier: InheritanceModifier = InheritanceModifier.FINAL,
    interfaces: List<TypePresentation> = emptyList(),
    constructorVisibilityModifier: VisibilityModifier = VisibilityModifier.PUBLIC,
    defaultConstructorParameters: List<Pair<TypeUsage, String>> = emptyList(),
    parentConstructorCall: ObjectCreationExpression? = null,
    final override val inheritedFrom: TypePresentation? = null,
    override var comments: ArrayList<String> = ArrayList(),
) : TypePresentation {
    final override val implementedInterfaces = interfaces.toSet()

    init {
        // interfaces have separate inheritance mechanism
        // enum and static classes cant inherited classes
        if (inheritanceModifier == InheritanceModifier.INTERFACE ||
            inheritanceModifier == InheritanceModifier.ENUM ||
            inheritanceModifier == InheritanceModifier.STATIC
        ) {
            assert(inheritedFrom == null)
        }

        if (inheritedFrom != null) {
            // type can be inherited only from abstract or open
            assert(
                inheritedFrom.inheritanceModifier == InheritanceModifier.ABSTRACT ||
                        inheritedFrom.inheritanceModifier == InheritanceModifier.OPEN
            )
            // if type is inherited - it can only be abstract, open or final
            assert(
                inheritanceModifier == InheritanceModifier.ABSTRACT ||
                        inheritanceModifier == InheritanceModifier.OPEN ||
                        inheritanceModifier == InheritanceModifier.FINAL
            )
        }
    }

    override val instanceType: InstanceTypeUsage by lazy { InstanceTypeImpl(this, false) }
    override val staticCounterPart: TypePresentation by lazy { StaticCounterPartTypeImpl(this) }
    override val defaultConstructor: ConstructorPresentation by lazy {
        createConstructor(
            defaultConstructorGraphId,
            constructorVisibilityModifier,
            parentConstructorCall,
            defaultConstructorParameters
        )
    }
    override val defaultValue: ObjectCreationExpression by lazy { ObjectCreationExpressionImpl(defaultConstructor) }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is TypeImpl)
            return false

        // different types should have different fqn names
        assert(this === other || this.fqnName != other.fqnName || this.staticCounterPart === other)

        return other === this
    }

    override fun hashCode(): Int {
        // type is uniquely identified by its fqn name
        return fqnName.hashCode()
    }

    final override val typeParts = mutableListOf<TypePart>()

    // this is the hardest method as it should check
    // 1. method really exists and relates to this type hierarchy
    // 2. not already overridden
    // 3. no collision with already defined methods
    // 4. signature compatability
    // 5. name compatability
    // 6. return type compatability
    // 7. available for override
    // 8. no final override in between 2 types
    override fun overrideMethod(methodToOverride: MethodPresentation): MethodPresentation {
        // 1. check not overridden
        // 2. check no same
        // 3. check no any other conflict
        TODO("Not yet implemented")
    }

    // throws if fqn + parameters already presented in available methods
    // this method intentionally does not allow overriding, use another method for it
    /**
     * Creates method from scratch. This method throws if proposed method already available in hierarchy.
     * Particularly, it throws if you try to implicitly override another method.
     * For overriding use [overrideMethod].
     */
    override fun createMethod(
        graphId: Int,
        name: String,
        visibility: VisibilityModifier,
        returnType: TypeUsage,
        inheritanceModifier: InheritanceModifier,
        parameters: List<Pair<TypeUsage, String>>,
    ): MethodPresentation {
        val methodToAdd = MethodImpl(graphId, this, name, visibility, returnType, inheritanceModifier, null, parameters)
        val collidedMethods = getMethods(name).filter { it.signature == methodToAdd.signature }

        if (collidedMethods.isEmpty()) {
            typeParts.add(methodToAdd)
            return methodToAdd
        } else {
            throw IllegalStateException("Method with the same signature already present: ${collidedMethods.size}")
        }
    }

    // throws if signature already present
    // or
    // parent call is incorrect - not provided or incorrect parent constructor targeted
    override fun createConstructor(
        graphId: Int,
        visibility: VisibilityModifier,
        parentConstructorCall: ObjectCreationExpression?,
        parameters: List<Pair<TypeUsage, String>>
    ): ConstructorPresentation {
        val constructorToAdd = ConstructorImpl(graphId, this, visibility, parentConstructorCall, parameters)
        // 3. assert constructor is created with parent call in case of need
        val collidedConstructors = typeParts
            .filterIsInstance<ConstructorPresentation>()
            .filter { it.signature == constructorToAdd.signature }

        if (collidedConstructors.isEmpty()) {
            typeParts.add(constructorToAdd)
            return constructorToAdd
        } else {
            throw IllegalStateException("Constructors with same signature already present: ${collidedConstructors.size}")
        }
    }

    // throws if name already present in hierachy
    override fun createField(
        name: String,
        type: TypePresentation,
        initialValue: CodeValue?
    ): FieldPresentation {
        TODO("Not yet implemented")
    }
}