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

import org.jacodb.api.core.CoreMethod
import org.jacodb.api.core.CoreType
import org.jacodb.api.core.cfg.ControlFlowGraph
import org.jacodb.panda.staticvm.cfg.PandaInst

sealed interface TypeNode : CoreType {
    val array: ArrayNode
}

data class ArrayNode(
    val dimensions: Int,
    private val wrappedType: SingleTypeNode
) : TypeNode {
    init { require(dimensions > 0) { "Cannot create array with $dimensions dimensions" } }

    override val typeName: String
        get() = wrappedType.typeName + "[]".repeat(dimensions)

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

sealed interface ObjectTypeNode : SingleTypeNode {
    /** qualified class/interface name */
    val name: String

    val classpath: PandaClasspath

    val directSuperClass: ClassTypeNode?

    val directSuperInterfaces: Set<InterfaceTypeNode>

    val declaredFields: HashMap<String, FieldNode>

    val declaredMethods: HashMap<String, MethodNode>

    val flags: AccessFlags

    override val typeName: String
        get() = name

    fun findFieldOrNull(name: String): FieldNode? = declaredFields[name] ?: directSuperClass?.findFieldOrNull(name)
    fun findMethodOrNull(name: String): MethodNode? = declaredMethods[name] ?: directSuperClass?.findMethodOrNull(name)

    fun findField(name: String) = requireNotNull(findFieldOrNull(name))
    fun findMethod(name: String) = requireNotNull(findMethodOrNull(name))
}

class FieldNode(
    val name: String,
    val enclosingClass: ObjectTypeNode,
    val type: TypeNode,
    val flags: AccessFlags
)

class MethodNode(
    val signature: String,
    val enclosingClass: ObjectTypeNode,
    val returnType: TypeNode,
    val parameterTypes: List<TypeNode>,
    val flags: AccessFlags
) : CoreMethod<PandaInst> {
    override fun flowGraph(): ControlFlowGraph<PandaInst> =
        enclosingClass.classpath.flowGraph(this)
}

class ClassTypeNode(
    override val classpath: PandaClasspath,
    override val name: String,
    override val directSuperClass: ClassTypeNode?,
    override val directSuperInterfaces: Set<InterfaceTypeNode>,
    override val flags: AccessFlags,
    override val declaredFields: HashMap<String, FieldNode> = hashMapOf(),
    override val declaredMethods: HashMap<String, MethodNode> = hashMapOf()
) : ObjectTypeNode

class InterfaceTypeNode(
    override val classpath: PandaClasspath,
    override val name: String,
    override val directSuperInterfaces: Set<InterfaceTypeNode>,
    override val flags: AccessFlags,
    override val declaredFields: HashMap<String, FieldNode> = hashMapOf(),
    override val declaredMethods: HashMap<String, MethodNode> = hashMapOf()
) : ObjectTypeNode {
    override val directSuperClass: ClassTypeNode?
        get() = null
}
