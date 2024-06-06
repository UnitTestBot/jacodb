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

package org.jacodb.panda.staticvm.ir

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jacodb.panda.staticvm.classpath.AccessFlags
import org.jacodb.panda.staticvm.classpath.PandaClass
import org.jacodb.panda.staticvm.classpath.PandaField
import org.jacodb.panda.staticvm.classpath.PandaInterface
import org.jacodb.panda.staticvm.classpath.PandaMethod
import org.jacodb.panda.staticvm.classpath.PandaProject
import org.jacodb.panda.staticvm.classpath.PandaType
import org.jacodb.panda.staticvm.utils.OneDirectionGraph
import org.jacodb.panda.staticvm.utils.applyFold
import org.jacodb.panda.staticvm.utils.inTopsortOrder
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream

@Serializable
data class PandaProgramIr(
    val classes: List<PandaClassIr> = emptyList(),
) {
    companion object {
        val json = Json {
            // ignoreUnknownKeys = true
            classDiscriminator = "opcode"
        }

        fun fromJson(jsonString: String): PandaProgramIr {
            return json.decodeFromString(jsonString)
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun from(inputStream: InputStream): PandaProgramIr {
            return json.decodeFromStream(inputStream)
        }

        fun from(file: File): PandaProgramIr {
            return from(file.inputStream().buffered())
        }

        fun from(path: Path): PandaProgramIr {
            return from(path.inputStream().buffered())
        }
    }

    fun addClassesHierarchyToPandaClasspath(project: PandaProject) {
        val classesMap = classes
            .filterNot { AccessFlags(it.accessFlags).isInterface }
            .associateBy { it.name }

        val graph = OneDirectionGraph(classesMap.values) {
            setOfNotNull(it.superClass?.let(classesMap::get))
        }

        requireNotNull(graph.inTopsortOrder()) {
            "Found cyclic inheritance"
        }.reversed().applyFold(project) {
            val superClass = it.superClass?.let { superClassName ->
                requireNotNull(findClassOrNull(superClassName))
            }
            val interfaces = it.interfaces.mapTo(hashSetOf()) { interfaceName ->
                requireNotNull(findInterfaceOrNull(interfaceName))
            }
            val pandaClass = PandaClass(
                project = this,
                name = it.name,
                directSuperClass = superClass,
                directSuperInterfaces = interfaces,
                flags = AccessFlags(it.accessFlags)
            )
            addClass(pandaClass)
        }
    }

    fun addInterfacesHierarchyToPandaClasspath(project: PandaProject) {
        val interfacesMap = classes
            .filter { AccessFlags(it.accessFlags).isInterface }
            .associateBy { it.name }

        val graph = OneDirectionGraph(interfacesMap.values) {
            it.interfaces.mapNotNullTo(hashSetOf()) { interfacesMap[it] }
        }

        requireNotNull(graph.inTopsortOrder()) {
            "Found cyclic inheritance"
        }.reversed().applyFold(project) {
            val interfaces = it.interfaces.mapTo(hashSetOf()) { interfaceName ->
                requireNotNull(findInterfaceOrNull(interfaceName))
            }
            val pandaInterface = PandaInterface(
                project = this,
                name = it.name,
                directSuperInterfaces = interfaces,
                flags = AccessFlags(it.accessFlags)
            )
            addInterface(pandaInterface)
        }
    }

    fun addFieldsToPandaClasspath(project: PandaProject) {
        classes.applyFold(project) { clazz ->
            val enclosingClass = requireNotNull(findClassOrInterfaceOrNull(clazz.name))
            clazz.fields.applyFold(this) { field ->
                val fieldType = requireNotNull(findTypeOrNull(field.type))
                val pandaField = PandaField(
                    enclosingClass = enclosingClass,
                    name = field.name,
                    type = fieldType,
                    flags = AccessFlags(field.accessFlags)
                )
                addField(pandaField)
            }
        }
    }

    fun addMethodsToPandaClasspath(project: PandaProject) {
        classes.applyFold(project) { clazz ->
            val enclosingClass = requireNotNull(findClassOrInterfaceOrNull(clazz.name))
            clazz.methods.applyFold(this) { method ->
                val returnType = findType(method.returnType)
                val isStatic = AccessFlags(method.accessFlags).isStatic
                val enclosing: List<PandaType> = if (isStatic) emptyList() else listOf(enclosingClass.type)
                val parameters: List<PandaType> = method.parameters.map { requireNotNull(findTypeOrNull(it)) }
                val parameterTypes = enclosing + parameters
                val pandaMethod = PandaMethod(
                    signature = method.signature,
                    name = method.name,
                    enclosingClass = enclosingClass,
                    returnType = returnType,
                    parameterTypes = parameterTypes,
                    flags = AccessFlags(method.accessFlags)
                )
                addMethod(pandaMethod)
            }
        }
    }

    fun addFlowGraphsToPandaClasspath(project: PandaProject) {
        classes.applyFold(project) { clazz ->
            clazz.methods.applyFold(this) { method ->
                val methodNode = requireNotNull(project.findMethod(method.signature))
                if (method.basicBlocks.isNotEmpty()) {
                    addFlowGraph(methodNode, method.basicBlocks)
                }
            }
        }
    }
}

@Serializable
data class PandaClassIr(
    val name: String,
    val simpleName: String,
    val superClass: String? = null,
    val interfaces: List<String> = emptyList(),
    val accessFlags: Int = 1,
    val fields: List<PandaFieldIr> = emptyList(),
    val methods: List<PandaMethodIr> = emptyList(),
)

@Serializable
data class PandaFieldIr(
    val name: String,
    val type: String,
    val accessFlags: Int,
)

@Serializable
data class PandaMethodIr(
    val name: String,
    val signature: String,
    val returnType: String,
    val parameters: List<String> = emptyList(),
    val accessFlags: Int,
    val basicBlocks: List<PandaBasicBlockIr> = emptyList(),
)

@Serializable
data class PandaBasicBlockIr(
    val id: Int,
    val predecessors: List<Int> = emptyList(),
    val successors: List<Int> = emptyList(),
    val insts: List<PandaInstIr> = emptyList(),
    val isCatchBegin: Boolean = false,
    val isTryBegin: Boolean = false,
    val isTryEnd: Boolean = false,
    private val handlerIds: List<Int> = emptyList(),
    private val handledTypes: List<String> = emptyList(),
) {
    data class Handler(val id: Int, val type: String?)

    val handlers: List<Handler>
        get() = handlerIds.zip(handledTypes) { id, type -> Handler(id, if (type == "finally") null else type) }
}
