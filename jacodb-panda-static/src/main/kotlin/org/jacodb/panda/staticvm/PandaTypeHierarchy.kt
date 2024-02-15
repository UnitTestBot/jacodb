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

import org.jacodb.api.*
import org.jacodb.panda.staticvm.*

open class TypeHierarchy protected constructor(
    private val project: PandaProject,
    private val nodes: HashSet<PandaObjectTypeNode> = hashSetOf()
) : Collection<PandaObjectTypeNode> by nodes {
    companion object {
        fun empty(project: PandaProject) = TypeHierarchy(project)
    }

    private val subTypes = hashMapOf<PandaObjectTypeNode, MutableList<PandaObjectTypeNode>>()

    fun addClass(
        typeName: PandaClassName,
        directSuperType: PandaTypeName? = null,
        vararg directSuperInterfaces: PandaTypeName): TypeHierarchy =
        addClass(typeName, directSuperType, directSuperInterfaces.asList())

    fun addClass(
        typeName: PandaClassName,
        directSuperType: PandaTypeName? = null,
        directSuperInterfaces: List<PandaTypeName> = emptyList()): TypeHierarchy = this.also {
        val type = PandaClassNode(
            project,
            typeName,
            nodes.filterIsInstance<PandaInterfaceNode>().filter { it.arkName in directSuperInterfaces },
            nodes.filterIsInstance<PandaClassNode>().find { it.arkName == directSuperType }
        )

        nodes.add(type)
        type.directSuperClass?.let { subTypes.getOrPut(it, ::mutableListOf).add(type) }
        type.directSuperInterfaces.forEach { subTypes.getOrPut(it, ::mutableListOf).add(type) }
    }

    fun addInterface(
        typeName: PandaClassName,
        vararg directSuperInterfaces: PandaTypeName): TypeHierarchy =
        addInterface(typeName, directSuperInterfaces.asList())

    fun addInterface(
        typeName: PandaClassName,
        directSuperInterfaces: List<PandaTypeName> = emptyList()): TypeHierarchy = this.also {
        val type = PandaInterfaceNode(
            project,
            typeName,
            nodes.filterIsInstance<PandaInterfaceNode>().filter { it.arkName in directSuperInterfaces }
        )

        nodes.add(type)
        type.directSuperInterfaces.forEach { subTypes.getOrPut(it, ::mutableListOf).add(type) }
    }

    fun traverseDown(from: PandaObjectTypeNode, selector: (PandaObjectTypeNode) -> Boolean = { true }, visitor: (PandaObjectTypeNode) -> Unit = {}) {
        val visited = hashSetOf<PandaObjectTypeNode>()
        fun dfs(node: PandaObjectTypeNode) = node.takeIf(selector)?.takeIf(visited::add)?.also(visitor)?.let {
            subTypes[it]?.forEach(::dfs)
        }
        dfs(from)
    }

    fun traverseUp(from: PandaObjectTypeNode, selector: (PandaObjectTypeNode) -> Boolean = { true }, visitor: (PandaObjectTypeNode) -> Unit = {}) {
        val visited = hashSetOf<PandaObjectTypeNode>()
        fun dfs(node: PandaObjectTypeNode) = node.takeIf(selector)?.takeIf(visited::add)?.also(visitor)
            ?.directSuperTypes?.forEach(::dfs)
        dfs(from)
    }

    fun subtypes(type: PandaTypeName) = hashSetOf<PandaObjectTypeNode>().also { traverseDown(
        node(type) ?: return@also, it::add
    ) }.map { it.arkName }

    fun supertypes(type: PandaTypeName) = hashSetOf<PandaObjectTypeNode>().also { traverseUp(
        node(type) ?: return@also, it::add
    ) }.map { it.arkName }

    private fun node(name: PandaTypeName) = nodes.find { it.arkName == name }

    private fun runWithValue(name: PandaTypeName, typeSelector: TypeHierarchy.(PandaTypeName) -> PandaType?): PandaType? =
        if (name.typeName.endsWith("[]"))
            runWithValue(name.typeName.removeSuffix("[]").pandaTypeName, typeSelector)?.let {
                PandaArrayTypeNode(project, it)
            }
        else typeSelector(this, name)

    fun findOrNull(name: PandaTypeName) = runWithValue(name) { node(name)
        ?: PrimitiveTypeHierarchy.types.find { it.vmType == name }?.let { PandaPrimitiveType(project, it.vmType) }
    }

    fun find(name: PandaTypeName) = findOrNull(name)
        ?: throw AssertionError("Unknown type $name")

    fun contains(name: PandaTypeName) = node(name) != null

    private fun superclasses(node: PandaClassNode) = generateSequence(node) { it.directSuperClass }.toList()
    private fun LCA(nodes: Collection<PandaClassNode>) = nodes.reduce { acc, next ->
        superclasses(acc).reversed().zip(superclasses(next).reversed())
            .takeWhile { it.first == it.second }
            .last().first
    }

    fun typeInBounds(lowerBounds: Collection<PandaTypeName>, upperBounds: Collection<PandaTypeName>): PandaType {
        if (lowerBounds.plus(upperBounds).filterIsInstance<PandaVMType>().isNotEmpty()) {
            if(!lowerBounds.plus(upperBounds).all { it is PandaVMType })
                throw AssertionError("Different kind of type bounds")
            return PandaPrimitiveType(project, PrimitiveTypeHierarchy.mostAccurateType(lowerBounds.plus(upperBounds).filterIsInstance<PandaVMType>()))
        }

        val visited = HashSet(nodes)
        lowerBounds.map(::node)
            .requireNoNulls().forEach { bound ->
            val lastVisited = hashSetOf<PandaObjectTypeNode>()
            traverseUp(bound, visitor = lastVisited::add)
            visited.removeIf { !lastVisited.contains(it) }
        }
        upperBounds.map(::node).requireNoNulls().forEach { bound ->
            val lastVisited = hashSetOf<PandaObjectTypeNode>()
            traverseDown(bound, visitor = lastVisited::add)
            visited.removeIf { !lastVisited.contains(it) }
        }
        return visited.filterIsInstance<PandaClassNode>().takeIf { it.isNotEmpty() }?.let(this::LCA) ?: visited.single()
    }
}
