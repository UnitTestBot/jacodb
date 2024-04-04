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
            ignoreUnknownKeys = true
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

    fun addClassesHierarchyToPandaClasspath(pandaProject: PandaProject) {
        val classesInfo = classes
            .filterNot { AccessFlags(it.accessFlags).isInterface }
            .associateBy { it.name }

        val graph = OneDirectionGraph(classesInfo.values) {
            setOfNotNull(it.superClass?.let(classesInfo::get))
        }

        requireNotNull(graph.inTopsortOrder()) {
            "Found cyclic inheritance"
        }.reversed().applyFold(pandaProject) {
            val superClass = it.superClass?.let { superClassName ->
                requireNotNull(findClassOrNull(superClassName))
            }
            val interfaces = it.interfaces.mapTo(hashSetOf()) { interfaceName ->
                requireNotNull(findInterfaceOrNull(interfaceName))
            }
            addClass(PandaClass(this, it.name, superClass, interfaces, AccessFlags(it.accessFlags)))
        }
    }

    fun addInterfacesHierarchyToPandaClasspath(pandaProject: PandaProject) {
        val interfacesInfo = classes
            .filter { AccessFlags(it.accessFlags).isInterface }
            .associateBy { it.name }

        val graph = OneDirectionGraph(interfacesInfo.values) {
            it.interfaces.mapNotNullTo(hashSetOf()) { interfacesInfo[it] }
        }

        requireNotNull(graph.inTopsortOrder()) {
            "Found cyclic inheritance"
        }.reversed().applyFold(pandaProject) {
            val interfaces = it.interfaces.mapTo(hashSetOf()) { interfaceName ->
                requireNotNull(findInterfaceOrNull(interfaceName))
            }
            addInterface(PandaInterface(this, it.name, interfaces, AccessFlags(it.accessFlags)))
        }
    }

    fun addFieldsToPandaClasspath(pandaProject: PandaProject) {
        classes.applyFold(pandaProject) { classInfo ->
            val enclosingClass = requireNotNull(findClassOrInterfaceOrNull(classInfo.name))
            classInfo.fields.applyFold(this) { fieldInfo ->
                val fieldType = requireNotNull(findTypeOrNull(fieldInfo.type))
                addField(PandaField(fieldInfo.name, enclosingClass, fieldType, AccessFlags(fieldInfo.accessFlags)))
            }
        }
    }

    fun addMethodsToPandaClasspath(pandaProject: PandaProject) {
        classes.applyFold(pandaProject) { classInfo ->
            val enclosingClass = requireNotNull(findClassOrInterfaceOrNull(classInfo.name))
            classInfo.methods.applyFold(this) { methodInfo ->
                val returnType = findType(methodInfo.returnType)
                val isStatic = AccessFlags(methodInfo.accessFlags).isStatic
                val parameterTypes =
                    (if (isStatic) emptyList<PandaType>() else listOf(enclosingClass.type)) + methodInfo.parameters.map {
                        requireNotNull(findTypeOrNull(it))
                    }
                val pandaMethod = PandaMethod(
                    methodInfo.signature,
                    methodInfo.name,
                    enclosingClass,
                    returnType,
                    parameterTypes,
                    AccessFlags(methodInfo.accessFlags)
                )
                addMethod(pandaMethod)
            }
        }
    }

    fun addFlowGraphsToPandaClasspath(pandaProject: PandaProject) {
        classes.applyFold(pandaProject) { classInfo ->
            classInfo.methods.applyFold(this) { methodInfo ->
                val methodNode = requireNotNull(pandaProject.findMethod(methodInfo.signature))
                if (methodInfo.basicBlocks.isNotEmpty())
                    addFlowGraph(methodNode, methodInfo.basicBlocks)
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
)

@Serializable
sealed interface PandaInstIr {
    val id: String
    val inputs: List<String>
    val users: List<String>
    val opcode: String
    val type: String
    val catchers: List<Int>

    fun <T> accept(visitor: PandaInstIrVisitor<T>): T
}
