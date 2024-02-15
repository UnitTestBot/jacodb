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

package org.jacodb.panda.staticvm

import org.jacodb.api.core.CoreMethod
import org.jacodb.api.core.cfg.ControlFlowGraph
import org.jacodb.api.jvm.cfg.JcInst

class PandaField(
    val project: PandaProject,
    val declaringClassType: PandaClassName,
    val type: PandaTypeName,
    val name: String,
    val access: AccessFlags
) {
    val isNullable: Boolean
        get() = !type.isPrimitive

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PandaField

        if (declaringClassType != other.declaringClassType) return false
        if (type != other.type) return false
        if (name != other.name) return false
        if (access != other.access) return false

        return true
    }

    override fun hashCode(): Int {
        var result = declaringClassType.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + access.hashCode()
        return result
    }

    override fun toString(): String {
        return "PandaField(declaringClassType=$declaringClassType, type=$type, name='$name', access=$access, isNullable=$isNullable)"
    }
}

class PandaMethod(
    val project: PandaProject,
    val declaringClassType: PandaClassName,
    val returnType: PandaTypeName,
    val args: List<PandaTypeName>,
    val name: String,
    val body: SimpleDirectedGraph<PandaBasicBlockInfo>?,
    val access: AccessFlags
) : CoreMethod<PandaInst> {
    private val flowGraphValue by lazy {
        if (body == null) PandaControlFlowGraph.empty()
        else PandaControlFlowGraph.of(this) 
    }

    override fun flowGraph(): ControlFlowGraph<PandaInst> = flowGraphValue

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PandaMethod

        if (declaringClassType != other.declaringClassType) return false
        if (returnType != other.returnType) return false
        if (args != other.args) return false
        if (name != other.name) return false
        if (access != other.access) return false

        return true
    }

    override fun hashCode(): Int {
        var result = declaringClassType.hashCode()
        result = 31 * result + returnType.hashCode()
        result = 31 * result + args.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + access.hashCode()
        return result
    }

    override fun toString(): String {
        return "PandaMethod(declaringClassType=$declaringClassType, returnType=$returnType, args=$args, name='$name', access=$access)"
    }

    val instList: List<JcInst>
        get() = TODO("Not yet implemented")
}

class PandaClass(
    val project: PandaProject,
    val declaredFields: List<PandaField>,
    val declaredMethods: List<PandaMethod>,
    val name: String,
    val superClassTypeName: PandaClassName?,
    val interfacesTypeNames: List<PandaClassName>,
    val access: AccessFlags
) {
    val superClass: PandaClass?
        get() = superClassTypeName?.let { project.classOf(it) }
    val interfaces: List<PandaClass>
        get() = project.typeOf(this).directSuperInterfaces.map {
            project.classOf(it.arkName)
        }
    val isInterface: Boolean
        get() = project.typeOf(this) is PandaInterfaceNode

    val fields: List<PandaField>
        get() = (superClass?.fields ?: listOf()) + declaredFields

    val methods: List<PandaMethod>
        get() = (superClass?.methods ?: listOf()) + declaredMethods

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PandaClass

        if (name != other.name) return false
        if (superClassTypeName != other.superClassTypeName) return false
        if (interfacesTypeNames != other.interfacesTypeNames) return false
        if (access != other.access) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (superClassTypeName?.hashCode() ?: 0)
        result = 31 * result + interfacesTypeNames.hashCode()
        result = 31 * result + access.hashCode()
        return result
    }
}
