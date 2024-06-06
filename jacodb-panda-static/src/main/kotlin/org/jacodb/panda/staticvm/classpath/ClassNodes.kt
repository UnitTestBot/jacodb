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

import org.jacodb.api.common.CommonClass
import org.jacodb.api.common.CommonField
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.panda.staticvm.cfg.PandaGraph
import org.jacodb.panda.staticvm.cfg.PandaInst

class PandaField(
    override val enclosingClass: PandaClassOrInterface,
    override val name: String,
    override val type: PandaType,
    val flags: AccessFlags,
) : CommonField {
    val signature: String
        get() = "${enclosingClass.name}.$name"
}

class PandaMethod(
    val signature: String,
    override val name: String,
    override val enclosingClass: PandaClassOrInterface,
    override val returnType: PandaType,
    val parameterTypes: List<PandaType>,
    val flags: AccessFlags,
) : CommonMethod {

    data class Parameter(
        override val type: PandaType,
        val index: Int,
        // val method: PandaMethod,
    ) : CommonMethodParameter {
        val name: String?
            get() = null
    }

    override val parameters: List<Parameter>
        get() = parameterTypes.mapIndexed { index, typeName ->
            // Parameter(typeName, index, this)
            Parameter(typeName, index)
        }

    override fun flowGraph(): PandaGraph {
        return enclosingClass.project.flowGraph(this)
    }

    // TODO: equals

    override fun toString(): String {
        return "${enclosingClass.name}::$name(${parameterTypes.joinToString { it.typeName }})"
    }
}

sealed interface PandaClassOrInterface : CommonClass {
    override val project: PandaProject

    override val name: String

    val simpleName: String
        get() = name.substringAfterLast('.')

    val directSuperClass: PandaClass?
    val directSuperInterfaces: Set<PandaInterface>

    val declaredFields: HashMap<String, PandaField>
    val declaredMethods: HashMap<String, PandaMethod>

    val flags: AccessFlags

    fun findFieldOrNull(name: String): PandaField? =
        declaredFields[name] ?: directSuperClass?.findFieldOrNull(name)

    fun findMethodOrNull(name: String): PandaMethod? =
        declaredMethods[name] ?: directSuperClass?.findMethodOrNull(name)

    fun findField(name: String) = requireNotNull(findFieldOrNull(name))
    fun findMethod(name: String) = requireNotNull(findMethodOrNull(name))

    fun findMethodBySimpleNameOrNull(name: String): PandaMethod? = declaredMethods.values.find { it.name == name }
        ?: directSuperClass?.findMethodBySimpleNameOrNull(name)

    fun findMethodBySimpleName(name: String) = requireNotNull(findMethodBySimpleNameOrNull(name))

    val fields: List<PandaField>
        get() = (directSuperClass?.fields ?: emptyList()) + declaredFields.values

    val methods: List<PandaMethod>
        get() = (directSuperClass?.methods ?: emptyList()) + declaredMethods.values

    val type: PandaObjectType
}

class PandaClass(
    override val project: PandaProject,
    override val name: String,
    override val directSuperClass: PandaClass?,
    override val directSuperInterfaces: Set<PandaInterface>,
    override val flags: AccessFlags,
    override val declaredFields: HashMap<String, PandaField> = hashMapOf(),
    override val declaredMethods: HashMap<String, PandaMethod> = hashMapOf(),
) : PandaClassOrInterface {
    override val type: PandaClassType
        get() = PandaClassType(project, this)

    override fun toString(): String = name
}

class PandaInterface(
    override val project: PandaProject,
    override val name: String,
    override val directSuperInterfaces: Set<PandaInterface>,
    override val flags: AccessFlags,
    override val declaredFields: HashMap<String, PandaField> = hashMapOf(),
    override val declaredMethods: HashMap<String, PandaMethod> = hashMapOf(),
) : PandaClassOrInterface {
    override val directSuperClass: PandaClass?
        get() = null

    override val type: PandaInterfaceType
        get() = PandaInterfaceType(project, this)

    override fun toString(): String = name
}
