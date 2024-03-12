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

package org.jacodb.panda.staticvm.classpath

import org.jacodb.api.common.*
import org.jacodb.api.common.cfg.ControlFlowGraph
import org.jacodb.panda.staticvm.cfg.PandaInst

sealed interface TypeNode : CommonType, CommonTypeName {
    val array: ArrayNode
}

data class ArrayNode(
    val dimensions: Int,
    private val wrappedType: SingleTypeNode
) : TypeNode {
    init { require(dimensions > 0) { "Cannot create array with $dimensions dimensions" } }

    override val typeName: String
        get() = wrappedType.typeName + "[]".repeat(dimensions)
    override val nullable: Boolean
        get() = true

    val elementType: TypeNode
        get() = if (dimensions == 1) wrappedType else ArrayNode(dimensions - 1, wrappedType)

    override val array: ArrayNode
        get() = ArrayNode(dimensions + 1, wrappedType)
}

/** any kind of non-array type */
sealed interface SingleTypeNode : TypeNode {
    override val array: ArrayNode
        get() = ArrayNode(1, this)
}

sealed interface ObjectTypeNode : SingleTypeNode, CommonClass, CommonClassType {
    /** qualified class/interface name */
    override val name: String

    override val simpleName: String
        get() = name

    override val project: PandaClasspath

    val directSuperClass: ClassTypeNode?

    val directSuperInterfaces: Set<InterfaceTypeNode>

    val declaredFields: HashMap<String, FieldNode>

    val declaredMethods: HashMap<String, MethodNode>

    val flags: AccessFlags

    override val typeName: String
        get() = name

    override val nullable: Boolean?
        get() = true

    fun findFieldOrNull(name: String): FieldNode? = declaredFields[name] ?: directSuperClass?.findFieldOrNull(name)
    fun findMethodOrNull(name: String): MethodNode? = declaredMethods[name] ?: directSuperClass?.findMethodOrNull(name)

    fun findField(name: String) = requireNotNull(findFieldOrNull(name))
    fun findMethod(name: String) = requireNotNull(findMethodOrNull(name))
}

class FieldNode(
    override val name: String,
    override val enclosingClass: ObjectTypeNode,
    override val type: TypeNode,
    val flags: AccessFlags
) : CommonClassField, CommonTypedField {
    override val signature: String?
        get() = "${enclosingClass.name}.$name"
    override val field: CommonClassField
        get() = this
}

class MethodNode(
    val signature: String,
    override val enclosingClass: ObjectTypeNode,
    override val returnType: TypeNode,
    val parameterTypes: List<TypeNode>,
    val flags: AccessFlags
) : CommonMethod<MethodNode, PandaInst> {
    override val name: String
        get() = signature

    data class Parameter(
        override val type: TypeNode,
        override val index: Int,
        override val method: CommonMethod<*, *>
    ) : CommonMethodParameter {
        override val name: String?
            get() = null
    }

    override val parameters: List<CommonMethodParameter>
        get() = parameterTypes.mapIndexed { index, typeNode ->
            Parameter(typeNode, index, this)
        }
    override fun flowGraph(): ControlFlowGraph<PandaInst> =
        enclosingClass.project.flowGraph(this)
}

class ClassTypeNode(
    override val project: PandaClasspath,
    override val name: String,
    override val directSuperClass: ClassTypeNode?,
    override val directSuperInterfaces: Set<InterfaceTypeNode>,
    override val flags: AccessFlags,
    override val declaredFields: HashMap<String, FieldNode> = hashMapOf(),
    override val declaredMethods: HashMap<String, MethodNode> = hashMapOf()
) : ObjectTypeNode

class InterfaceTypeNode(
    override val project: PandaClasspath,
    override val name: String,
    override val directSuperInterfaces: Set<InterfaceTypeNode>,
    override val flags: AccessFlags,
    override val declaredFields: HashMap<String, FieldNode> = hashMapOf(),
    override val declaredMethods: HashMap<String, MethodNode> = hashMapOf()
) : ObjectTypeNode {
    override val directSuperClass: ClassTypeNode?
        get() = null
}
