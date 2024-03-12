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

import org.jacodb.api.common.Project
import org.jacodb.api.common.cfg.ControlFlowGraph
import org.jacodb.panda.staticvm.cfg.PandaControlFlowGraph
import org.jacodb.panda.staticvm.cfg.PandaInst
import org.jacodb.panda.staticvm.ir.PandaBasicBlockIr
import org.jacodb.panda.staticvm.ir.PandaProgramIr

class PandaClasspath : Project {
    private data class TreeEntry<T>(
        val depth: Int,
        val parent: TreeEntry<T>?,
        val data: T
    )

    val objectType = ClassTypeNode(
        this,
        "std.core.Object",
        null,
        emptySet(),
        AccessFlags(1)
    )
    
    private val classesIndex = hashMapOf<String, TreeEntry<ClassTypeNode>>()
    private val interfacesIndex = hashMapOf<String, InterfaceTypeNode>()
    private val methodIndex = hashMapOf<String, MethodNode>()
    
    fun addClass(node: ClassTypeNode) : Boolean {
        if (classesIndex.containsKey(node.name))
            return false
        
        require(interfacesIndex.keys.containsAll(node.directSuperInterfaces.map { it.name }))
        val parentNode = node.directSuperClass?.let { requireNotNull(classesIndex[it.name]) }
            ?: classesIndex[objectType.name]
        
        classesIndex[node.name] = TreeEntry(parentNode?.depth?.inc() ?: 0, parentNode, node)
        return true
    }

    init {
        addClass(objectType)
    }

    fun addInterface(node: InterfaceTypeNode) : Boolean {
        require(interfacesIndex.keys.containsAll(node.directSuperInterfaces.map { it.name }))
        return interfacesIndex.putIfAbsent(node.name, node) == null
    }

    fun addField(field: FieldNode) : Boolean {
        val enclosingClass = requireNotNull(findClassOrInterfaceOrNull(field.enclosingClass))
        return enclosingClass.declaredFields.putIfAbsent(field.name, field) == null
    }

    fun addMethod(method: MethodNode) : Boolean {
        val enclosingClass = requireNotNull(findClassOrInterfaceOrNull(method.enclosingClass))
        method.parameterTypes.forEach { requireNotNull(findTypeOrNull(it.typeName)) }
        if (methodIndex.containsKey(method.signature))
            return false
        methodIndex[method.signature] = method
        enclosingClass.declaredMethods[method.signature] = method
        return true
    }

    fun addFlowGraph(method: MethodNode, basicBlocksInfo: List<PandaBasicBlockIr>) : Boolean {
        val enclosingClass = requireNotNull(findClassOrInterfaceOrNull(method.enclosingClass))
        require(enclosingClass.declaredMethods.containsValue(method))
        return flowGraphs.putIfAbsent(method, PandaControlFlowGraph.of(method, basicBlocksInfo)) == null
    }

    fun findClassOrNull(name: String): ClassTypeNode? = classesIndex[name]?.data
    fun findInterfaceOrNull(name: String): InterfaceTypeNode? = interfacesIndex[name]
    fun findClassOrInterfaceOrNull(name: String) = classesIndex[name]?.data ?: interfacesIndex[name]

    fun findClassOrNull(node: ClassTypeNode) = findClassOrNull(node.name)?.takeIf { it == node }
    fun findInterfaceOrNull(node: InterfaceTypeNode) = findInterfaceOrNull(node.name)?.takeIf { it == node }
    fun findClassOrInterfaceOrNull(node: ObjectTypeNode) = findClassOrInterfaceOrNull(node.name)?.takeIf { it == node }

    fun findMethod(name: String) = requireNotNull(methodIndex[name]) {
        "Not found method $name"
    }

    companion object {
        fun fromProgramInfo(programInfo: PandaProgramIr): PandaClasspath {
            val pandaClasspath = PandaClasspath()
            programInfo.addInterfacesHierarchyToPandaClasspath(pandaClasspath)
            programInfo.addClassesHierarchyToPandaClasspath(pandaClasspath)
            programInfo.addFieldsToPandaClasspath(pandaClasspath)
            programInfo.addMethodsToPandaClasspath(pandaClasspath)
            programInfo.addFlowGraphsToPandaClasspath(pandaClasspath)
            return pandaClasspath
        }
    }

    override fun findTypeOrNull(name: String): TypeNode? =
        if (name.endsWith("[]"))
            findTypeOrNull(name.removeSuffix("[]"))?.array
        else PandaPrimitives.findPrimitiveOrNull(name) ?: findClassOrInterfaceOrNull(name)

    fun findType(name: String) = requireNotNull(findTypeOrNull(name)) {
        "Not found type $name"
    }

    fun findClass(name: String) = requireNotNull(findClassOrNull(name)) {
        "Not found class $name"
    }
    fun findClassOrInterface(name: String) = requireNotNull(findClassOrInterfaceOrNull(name))
    fun getElementType(name: String) = if (name.endsWith("[]")) findType(name.removeSuffix("[]"))
        else throw IllegalArgumentException("Expected array type")

    override fun close() {
        // TODO: not necessary for now
    }

    private val flowGraphs = hashMapOf<MethodNode, ControlFlowGraph<PandaInst>>()

    val classes: List<ClassTypeNode>
        get() = classesIndex.values.map { it.data }

    val methods: List<MethodNode>
        get() = methodIndex.values.toList()

    fun flowGraph(method: MethodNode) = flowGraphs.getOrPut(method, PandaControlFlowGraph.Companion::empty)

    private fun ancestors(node: TreeEntry<ClassTypeNode>): List<ClassTypeNode> =
        (node.parent?.let { ancestors(it) } ?: emptyList()).plus(node.data)

    fun commonClassType(types: Collection<ClassTypeNode>): ClassTypeNode? {
        if (types.isEmpty())
            return objectType
        val nodes = types.map { requireNotNull(classesIndex[it.typeName]) }
        val paths = nodes.map { ancestors(it) }

        return paths.reduce { p1, p2 ->
            p1.zip(p2) { t1, t2 -> t1.takeIf { t1 == t2 } }.filterNotNull()
        }.lastOrNull()
    }

    fun commonType(types: Collection<TypeNode>): TypeNode? {
        if (types.any { it is ArrayNode }) {
            return types.takeIf { it.all { it is ArrayNode } }
                ?.filterIsInstance<ArrayNode>()
                ?.map { it.elementType }
                ?.let(this::commonType)
                ?.array
        }
        if (types.any { it is PandaPrimitiveTypeNode }) {
            return types.takeIf { it.all { it is PandaPrimitiveTypeNode } }
                ?.filterIsInstance<PandaPrimitiveTypeNode>()
                ?.max()
        }
        return commonClassType(types.filterIsInstance<ClassTypeNode>())
    }
}
