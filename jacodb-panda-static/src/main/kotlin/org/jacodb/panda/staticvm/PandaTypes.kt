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

import org.jacodb.api.core.CoreType
import org.jacodb.api.core.TypeName

sealed interface PandaTypeName : TypeName {
    override val typeName: String
}

enum class PandaVMType(
    override val typeName: String
) : PandaTypeName {
    VOID("void"),
    BOOL("u1"),
    BYTE("i8"),
    UBYTE("u8"),
    SHORT("i16"),
    USHORT("u16"),
    B32("b32"),
    INT("i32"),
    UINT("u32"),
    LONG("i64"),
    ULONG("u64"),
    B64("b64"),
    FLOAT("f32"),
    DOUBLE("f64"),
    REF("ref"),
    ANY("any");

    override fun toString(): String = typeName
}

data class PandaArrayName(override val typeName: String) : PandaTypeName {
    override fun toString(): String = "$typeName[]"
}
data class PandaClassName(override val typeName: String) : PandaTypeName {
    override fun toString(): String = typeName
}

val String.pandaClassName: PandaClassName
    get() = PandaClassName(this)

val String.pandaTypeName: PandaTypeName
    get() = enumValues<PandaVMType>().find { it.typeName == this } ?: PandaClassName(this)

sealed interface PandaType : CoreType {
    val classpath: PandaProject
    val arkName: PandaTypeName
    override val typeName: String
        get() = arkName.typeName
}

data class PandaPrimitiveType(
    override val classpath: PandaProject,
    override val arkName: PandaTypeName
) : PandaType {
    override val typeName: String
        get() = arkName.typeName

    override fun toString() = typeName
}

data class PandaArrayTypeNode(
    override val classpath: PandaProject,
    val elementType: PandaType
) : PandaType {
    override val arkName: PandaTypeName
        get() = PandaArrayName("$elementType[]")
    val dimensions: Int
        get() = 1 + when (elementType) {
            is PandaArrayTypeNode -> elementType.dimensions
            else -> 0
        }

    override fun toString() = typeName
}

sealed interface PandaObjectTypeNode : PandaType {
    override val arkName: PandaClassName
    val directSuperTypes: List<PandaObjectTypeNode>
    val directSuperInterfaces: List<PandaInterfaceNode>
}

sealed interface PandaClassOrInterfaceTypeNode : PandaObjectTypeNode {
    val pandaClass: PandaClass
        get() = classpath.findClassOrNull(arkName.typeName)
            ?: throw AssertionError("$arkName class not found")

    val superType: PandaClassNode?
    val interfaces: List<PandaInterfaceNode>
        get() = directSuperInterfaces
}

data class PandaInterfaceNode(
    override val classpath: PandaProject,
    override val arkName: PandaClassName,
    override val directSuperInterfaces: List<PandaInterfaceNode>
) : PandaClassOrInterfaceTypeNode {
    override val superType: PandaClassNode?
        get() = null
    override val directSuperTypes: List<PandaObjectTypeNode>
        get() = directSuperInterfaces

    override fun toString() = typeName
}

data class PandaClassNode(
    override val classpath: PandaProject,
    override val arkName: PandaClassName,
    override val directSuperInterfaces: List<PandaInterfaceNode>,
    val directSuperClass: PandaClassNode?
) : PandaClassOrInterfaceTypeNode {
    override val superType: PandaClassNode?
        get() = directSuperClass
    override val directSuperTypes: List<PandaObjectTypeNode>
        get() = listOfNotNull(directSuperClass).plus(directSuperInterfaces)

    override fun toString() = typeName
}

data class ArkPrimitiveTypeNode(
    val vmType: PandaVMType,
    val parent: ArkPrimitiveTypeNode?
)

object PrimitiveTypeHierarchy {
    val void = ArkPrimitiveTypeNode(PandaVMType.VOID, null)
    val any = ArkPrimitiveTypeNode(PandaVMType.ANY, null)
    val b32 = ArkPrimitiveTypeNode(PandaVMType.B32, any)
    val u1 = ArkPrimitiveTypeNode(PandaVMType.BOOL, b32)
    val i8 = ArkPrimitiveTypeNode(PandaVMType.BYTE, b32)
    val u8 = ArkPrimitiveTypeNode(PandaVMType.UBYTE, b32)
    val i16 = ArkPrimitiveTypeNode(PandaVMType.SHORT, b32)
    val u16 = ArkPrimitiveTypeNode(PandaVMType.USHORT, b32)
    val i32 = ArkPrimitiveTypeNode(PandaVMType.INT, b32)
    val u32 = ArkPrimitiveTypeNode(PandaVMType.UINT, b32)
    val f32 = ArkPrimitiveTypeNode(PandaVMType.FLOAT, b32)
    val b64 = ArkPrimitiveTypeNode(PandaVMType.B64, any)
    val i64 = ArkPrimitiveTypeNode(PandaVMType.LONG, b64)
    val u64 = ArkPrimitiveTypeNode(PandaVMType.ULONG, b64)
    val f64 = ArkPrimitiveTypeNode(PandaVMType.DOUBLE, b64)

    val types = listOf(
        void,
        b32,
        u1,
        i8, u8,
        i16, u16,
        i32, u32,
        f32,
        b64,
        i64, u64,
        f64,
        any
    )

    private fun supertypes(node: ArkPrimitiveTypeNode) = generateSequence(node) { it.parent }.toList()

    fun supertypes(type: PandaTypeName) = types.find { it.vmType == type }?.let {
        supertypes(it).map { it.vmType }
    }

    fun mostAccurateType(bounds: Collection<PandaVMType>) = bounds.map { type ->
        types.find { it.vmType == type } ?: throw IllegalArgumentException("Not found primitive type $type")
    }.reduce { acc, next ->
        if (next in supertypes(acc)) acc else if (acc in supertypes(next)) next else
            throw IllegalArgumentException("No common primitive type for ${bounds.toList()}")
    }.vmType
}

val PandaTypeName.isPrimitive: Boolean get() = PrimitiveTypeHierarchy.types.find { it.vmType == this } != null
