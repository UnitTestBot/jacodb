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

import TypeHierarchy
import org.jacodb.api.core.Project

class PandaProject(
    val classes: MutableSet<PandaClass>,
    val fields: MutableSet<PandaField>,
    val methods: MutableSet<PandaMethod>
) : Project<PandaType> {

    val typeHierarchy = TypeHierarchy.empty(this)

    fun findClassOrNull(name: String): PandaClass? =
        classes.find { it.name == name }

    fun findClasses(name: String): Set<PandaClass> =
        classes.filter { it.name == name }.toSet()

    override fun findTypeOrNull(name: String): PandaType? =
        typeHierarchy.findOrNull(name.pandaTypeName)

    override fun close() {
        TODO("Not yet implemented")
    }

    fun typeOf(pandaClass: PandaClass): PandaObjectTypeNode {
        val type = findTypeOrNull(pandaClass.name)
        if (type is PandaObjectTypeNode)
            return type
        else throw AssertionError("Type not found")
    }

    fun classOf(classType: PandaClassName): PandaClass = classes.find { it.name == classType.typeName }
        ?: throw AssertionError("Class of type $classType not found")

    val void: PandaPrimitiveType
        get() = PandaPrimitiveType(this, PandaVMType.VOID)
    val boolean: PandaPrimitiveType
        get() = PandaPrimitiveType(this, PandaVMType.BOOL)
    val short: PandaPrimitiveType
        get() = PandaPrimitiveType(this, PandaVMType.SHORT)
    val int: PandaPrimitiveType
        get() = PandaPrimitiveType(this, PandaVMType.INT)
    val long: PandaPrimitiveType
        get() = PandaPrimitiveType(this, PandaVMType.LONG)
    val float: PandaPrimitiveType
        get() = PandaPrimitiveType(this, PandaVMType.FLOAT)
    val double: PandaPrimitiveType
        get() = PandaPrimitiveType(this, PandaVMType.DOUBLE)
    val byte: PandaPrimitiveType
        get() = PandaPrimitiveType(this, PandaVMType.BYTE)
    val char: PandaPrimitiveType
        get() = PandaPrimitiveType(this, PandaVMType.BYTE)
    val nullType: PandaPrimitiveType
        get() = PandaPrimitiveType(this, PandaVMType.REF)
    val objectClass: PandaClass
        get() = classes.find { it.name == "std.core.object" }
            ?: throw AssertionError("Object class not found")
    val objectType: PandaClassNode
        get() = findTypeOrNull("std.core.object") as? PandaClassNode
            ?: throw AssertionError("Object type not found")
    val stringType: PandaClassNode
        get() = findTypeOrNull("std.core.String") as? PandaClassNode
            ?: throw AssertionError("String type not found")
    val serializableClass: PandaObjectTypeNode
        get() = TODO()
    val cloneableClass: PandaObjectTypeNode
        get() = TODO()

    companion object {
        fun blank() = PandaProject(hashSetOf(), hashSetOf(), hashSetOf())
    }
}
