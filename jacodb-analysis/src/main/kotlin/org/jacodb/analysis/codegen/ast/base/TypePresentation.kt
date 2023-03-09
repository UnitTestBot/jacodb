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

package org.jacodb.analysis.codegen.ast.base

import org.jacodb.analysis.codegen.ast.impl.TypeImpl

/**
 * Class, interface, abstract class, structure, enum.
 */
interface TypePresentation : CodePresentation, VisibilityOwner, NameOwner, Inheritable {
    companion object {
        val voidType: TypePresentation = TypeImpl("void")
    }

    // most of the time this is ObjectCreationExpression - for open and final classes
    // in case of abstract, static, interface and enum - this would throw exception
    val defaultValue: CodeValue

    // if created class is not abstract - we will find all methods that do not have implementation
    // and provide default overload for it. Default means completely empty body, same parameters and return type.
    val typeParts: Collection<TypePart>

    // there are no way to add interface as we expect:
    // 1. all interface hierarchy will be predefined beforehand - in graph
    // 2. all class hierarchy also will be predefined beforehand - as tree
    // 3. we will know about abstract class to interface mappings
    // 4. we will topologically sort that class-interface graph and generate hierarchy accordingly
    val implementedInterfaces: Collection<TypePresentation>

    // as we know class-interface hierarchy - we do not need to redefine who we inherited from
    override val inheritedFrom: TypePresentation?

    val defaultConstructor: ConstructorPresentation
    val staticCounterPart: TypePresentation
    val instanceType: InstanceTypeUsage

    fun overrideMethod(methodToOverride: MethodPresentation): MethodPresentation
    fun createMethod(
        graphId: Int,
        name: String = "methodFor$graphId",
        visibility: VisibilityModifier = VisibilityModifier.PUBLIC,
        returnType: TypeUsage = voidType.instanceType,
        inheritanceModifier: InheritanceModifier = InheritanceModifier.FINAL,
        parameters: List<Pair<TypeUsage, String>> = emptyList(),
    ): MethodPresentation

    fun createConstructor(
        graphId: Int,
        visibility: VisibilityModifier = VisibilityModifier.PUBLIC,
        parentConstructorCall: ObjectCreationExpression? = null,
        parameters: List<Pair<TypeUsage, String>> = emptyList()
    ): ConstructorPresentation

    fun createField(name: String, type: TypePresentation, initialValue: CodeValue? = null): FieldPresentation

    val implementedMethods: Collection<MethodPresentation>
        get() = typeParts.filterIsInstance<MethodPresentation>()
    val allAvailableMethods: Collection<MethodPresentation>
        get() = (implementedInterfaces.flatMap { it.allAvailableMethods })
            .union(inheritedFrom?.allAvailableMethods ?: mutableListOf())
            .union(implementedMethods)

    val constructors: Collection<ConstructorPresentation>
        get() = typeParts.filterIsInstance<ConstructorPresentation>()

    val implementedFields: Collection<FieldPresentation>
        get() = typeParts.filterIsInstance<FieldPresentation>()
    val allAvailableFields: Collection<FieldPresentation>
        get() = implementedFields.union(inheritedFrom?.allAvailableFields ?: emptyList())

    fun getImplementedField(name: String): FieldPresentation? = implementedFields.singleOrNull { it.shortName == name }
    fun getField(name: String): FieldPresentation? = allAvailableFields.singleOrNull { it.shortName == name }
    fun getImplementedFields(type: TypeUsage): Collection<FieldPresentation> =
        implementedFields.filter { it.usage == type }

    fun getFields(type: TypeUsage): Collection<FieldPresentation> = allAvailableFields.filter { it.usage == type }

    fun getImplementedMethods(name: String): Collection<MethodPresentation> =
        implementedMethods.filter { it.shortName == name }

    fun getMethods(name: String): Collection<MethodPresentation> = allAvailableMethods.filter { it.shortName == name }
}