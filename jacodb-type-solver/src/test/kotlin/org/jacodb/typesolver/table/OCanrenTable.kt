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

package org.jacodb.typesolver.table

import org.jacodb.api.*
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.objectClass
import org.jacodb.api.ext.toType
import org.jacodb.impl.types.signature.*
import org.jacodb.impl.types.typeParameters
import org.jacodb.typesolver.table.JvmWildcardPolarity.Extends
import org.jacodb.typesolver.table.JvmWildcardPolarity.Super
import org.jacodb.typesolver.table.PrimitiveType.*
import kotlin.Array

enum class JvmWildcardPolarity {
    Extends,
    Super;
}

sealed interface JvmTypeArgument

data class Type(val type: JvmType) : JvmTypeArgument
data class Wildcard(val bound: Pair<JvmWildcardPolarity, JvmType>?) : JvmTypeArgument

sealed interface JvmType

data class Array(val elementType: JvmType) : JvmType
//data class Class(val cname: String, val typeParameters: Array<out JvmTypeArgument>) : JvmType()
//data class Interface(val iname: String, val typeParameters: Array<out JvmTypeArgument>) : JvmType()
data class Class(val cname: String, val typeArguments: Array<out JvmTypeArgument>) : JvmType
data class Interface(val iname: String, val typeArguments: Array<out JvmTypeArgument>) : JvmType
data class Var(val id: String, val index: Int, val upb: JvmType, val lwb: JvmType?) : JvmType
object Null : JvmType {
    override fun toString(): String = "Null"
}
object UnboundWildcardAsJvmType : JvmType
data class Intersect(val types: Array<out JvmType>) : JvmType
enum class PrimitiveType(val jvmName: String) : JvmType {
    PrimitiveByte("byte"),
    PrimitiveShort("short"),
    PrimitiveInt("int"),
    PrimitiveLong("long"),
    PrimitiveFloat("float"),
    PrimitiveDouble("double"),
    PrimitiveBoolean("boolean"),
    PrimitiveChar("int"),
    PrimitiveVoid("void");
}

sealed class JvmDeclaration

data class ClassDeclaration(val cname: String, val params: Array<out JvmType>, val `super`: JvmType?, val supers: Array<out JvmType>) : JvmDeclaration()
data class InterfaceDeclaration(val iname: String, val iparams: Array<out JvmType>, val isupers: Array<out JvmType>) : JvmDeclaration()

data class ClassesTable(val table: Array<out JvmDeclaration>)

private fun UnboundWildcardAsJvmType.toJvmTypeArgument(): Wildcard = Wildcard(null)
private fun JvmType.toJvmTypeArgument(): JvmTypeArgument = Type(this)

fun JcClassOrInterface.toJvmDeclaration(classpath: JcClasspath): JvmDeclaration {
//    if ("kotlin.text.RegexKt\$fromInt\$1\$1" in name) {
//        println()
//    }

    val type = toType()
    val typeParams = typeParameters.mapIndexed { index, param -> param.toJvmType(index, classpath) }.toTypedArray()
    val supers = type.interfaces.map { it.toJvmType(classpath) }.toTypedArray()

    return when {
        isInterface -> InterfaceDeclaration(name, typeParams, supers)
        else -> ClassDeclaration(name, typeParams, type.superType?.toJvmType(classpath), supers)
    }
}

private fun JcClassType.toJvmType(classpath: JcClasspath): JvmType {
    val typeParams = typeArguments.mapIndexed { index, param ->
        param.toJvmTypeArgument(index, classpath)
    }.toTypedArray()

    val name = jcClass.name
    return if (isInterface) Interface(name, typeParams) else Class(name, typeParams)
}

private fun JcType.toJvmType(index: Int, classpath: JcClasspath): JvmType = when (this) {
    is JcRefType -> toJvmType(index, classpath)
    is JcPrimitiveType -> typeName.toPrimitiveType()
    else -> error("Unknown JcType $this")
}

private fun JcRefType.toJvmType(index: Int, classpath: JcClasspath): JvmType = when (this) {
    is JcArrayType -> Array(elementType.toJvmType(index, classpath))
    is JcClassType -> toJvmType(classpath)
    is JcTypeVariable -> toJvmType(index, classpath)
    is JcBoundedWildcard -> error("Unexpected $this")
    is JcUnboundWildcard -> error("Unexpected $this")
    else -> error("Unknown ref type $this")
}

private fun JcRefType.toJvmTypeArgument(index: Int, classpath: JcClasspath): JvmTypeArgument = when (this) {
    is JcArrayType -> Array(elementType.toJvmType(index, classpath)).toJvmTypeArgument()
    is JcClassType -> toJvmType(classpath).toJvmTypeArgument()
    is JcTypeVariable -> toJvmType(index, classpath).toJvmTypeArgument()
    is JcBoundedWildcard -> toJvmTypeArgument(index, classpath)
    is JcUnboundWildcard -> toJvmTypeArgument(index, classpath)
    else -> error("Unknown ref type $this")
}

private fun JcBoundedWildcard.toJvmTypeArgument(index: Int, classpath: JcClasspath): JvmTypeArgument {
    require(lowerBounds.isEmpty() != upperBounds.isEmpty())

    if (lowerBounds.isNotEmpty()) {
        require(lowerBounds.size == 1) {
            TODO()
        }

        return Wildcard(Super to lowerBounds.single().toJvmType(index, classpath))
    }

    require(upperBounds.size == 1) {
        TODO()
    }
    return Wildcard(Extends to upperBounds.single().toJvmType(index, classpath))
}

private fun JcUnboundWildcard.toJvmTypeArgument(index: Int, classpath: JcClasspath): JvmTypeArgument = Wildcard(null)

fun JcTypeVariable.toJvmType(index: Int, classpath: JcClasspath): JvmType {
    val typeBounds = bounds

    val upperBound = when {
        typeBounds.isEmpty() -> classpath.objectClass.toJvmType(classpath)
        typeBounds.size == 1 -> typeBounds.single().toJvmType(index, classpath)
        else -> Intersect(typeBounds.map { it.toJvmType(index, classpath) }.toTypedArray())
    }

    return Var(symbol, index, upperBound, null)
}

fun JvmTypeParameterDeclaration.toJvmType(index: Int, classpath: JcClasspath): JvmType {
    val typeBounds = bounds

    val upperBound = when {
        typeBounds.isNullOrEmpty() -> classpath.objectClass.toJvmType(classpath)
        typeBounds.size == 1 -> typeBounds.single().toJvmType(classpath, index)
        else -> Intersect(typeBounds.map { it.toJvmType(classpath, index) }.toTypedArray())
    }

    return Var(symbol, index, upperBound, null)
}

fun JcClassOrInterface.toJvmType(classpath: JcClasspath): JvmType {
//    val typeParams = typeParameters.map { it.toJvmTypeArgument(classpath) }.toTypedArray()
//    val typeParams = typeParameters.mapIndexed { index, param -> param.toJvmType(index, classpath) }.toTypedArray()
    val typeParams = toType().typeArguments.mapIndexed { index, param -> param.toJvmTypeArgument(index, classpath) }.toTypedArray()

    return if (isInterface) Interface(name, typeParams) else Class(name, typeParams)
}

/*private fun JvmTypeParameterDeclaration.toJvmTypeArgument(classpath: JcClasspath): JvmTypeArgument {
    val typeBounds = bounds
    require(typeBounds != null) {
        "Type bounds should not be null for $this"
    }

    require(typeBounds.isNotEmpty()) {
        "Type bounds should not be empty for $this"
    }

    return typeBounds.singleOrNull()?.toJvmTypeArgument(classpath) ?: classpath.objectClass.toJvmTypeArgument(classpath)
}*/

/*private fun JcClassOrInterface.toJvmTypeArgument(classpath: JcClasspath): JvmTypeArgument =
    Type(classpath.findClass(name).toJvmType(classpath))*/

/*private fun org.jacodb.impl.types.signature.JvmType.toJvmTypeArgument(classpath: JcClasspath): JvmTypeArgument = when (this) {
    is JvmArrayType -> Type(toJvmType(classpath))
    is JvmClassRefType -> Type(classpath.findClass(name).toJvmType(classpath))
    is JvmParameterizedType -> Type(toJvmType(classpath))
    is JvmPrimitiveType -> TODO()
    is JvmTypeVariable -> TODO()
    is JvmParameterizedType.JvmNestedType -> TODO()
    is JvmBoundWildcard.JvmLowerBoundWildcard -> Wildcard(JvmWildcardPolarity.Super to bound.toJvmType(classpath))
    is JvmBoundWildcard.JvmUpperBoundWildcard -> Wildcard(JvmWildcardPolarity.Extends to bound.toJvmType(classpath))
    JvmUnboundWildcard -> Wildcard(null)
}*/

private fun org.jacodb.impl.types.signature.JvmType.toJvmType(classpath: JcClasspath, index: Int = 0): JvmType = when (this) {
    is JvmArrayType -> Array(elementType.toJvmType(classpath))
    is JvmClassRefType -> classpath.findClass(name).toJvmType(classpath)
//    is JvmParameterizedType -> Class(name, parameterTypes.map { it.toJvmTypeArgument(classpath) }.toTypedArray())
    is JvmParameterizedType -> Class(name, parameterTypes.mapIndexed { index, param -> param.toJvmTypeArgument(index, classpath) }.toTypedArray())
    is JvmTypeVariable -> toJvmType(index /*It is already mentioned variable with an unknown index*/, classpath)
    is JvmParameterizedType.JvmNestedType -> Class(name, parameterTypes.mapIndexed { index, param -> param.toJvmTypeArgument(index, classpath) }.toTypedArray())
    is JvmPrimitiveType -> toJvmType()
    is JvmBoundWildcard.JvmUpperBoundWildcard -> toJvmType(index, classpath)
    is JvmBoundWildcard.JvmLowerBoundWildcard -> toJvmType(index, classpath)
    JvmUnboundWildcard -> UnboundWildcardAsJvmType
}

private fun org.jacodb.impl.types.signature.JvmType.toJvmTypeArgument(index: Int, classpath: JcClasspath) = when (this) {
    is JvmArrayType,
    is JvmClassRefType,
    is JvmParameterizedType.JvmNestedType,
    is JvmParameterizedType,
    is JvmPrimitiveType,
    is JvmTypeVariable -> toJvmType(classpath, index).toJvmTypeArgument()
    is JvmBoundWildcard.JvmLowerBoundWildcard -> toJvmTypeArgument(index, classpath)
    is JvmBoundWildcard.JvmUpperBoundWildcard -> toJvmTypeArgument(index, classpath)
    JvmUnboundWildcard -> Wildcard(null)
}

private fun JvmPrimitiveType.toJvmType(): PrimitiveType = ref.toPrimitiveType()

private fun String.toPrimitiveType(): PrimitiveType = when (this) {
    PredefinedPrimitives.Byte -> PrimitiveByte
    PredefinedPrimitives.Short -> PrimitiveShort
    PredefinedPrimitives.Int -> PrimitiveInt
    PredefinedPrimitives.Long -> PrimitiveLong
    PredefinedPrimitives.Float -> PrimitiveFloat
    PredefinedPrimitives.Double -> PrimitiveDouble
    PredefinedPrimitives.Boolean -> PrimitiveBoolean
    PredefinedPrimitives.Char -> PrimitiveChar
    PredefinedPrimitives.Void -> PrimitiveVoid
    else -> error("Unknown primitive type $this")
}

private fun JvmBoundWildcard.JvmLowerBoundWildcard.toJvmTypeArgument(index: Int, classpath: JcClasspath): JvmTypeArgument {
    require(index >= 0)

    return Wildcard(Super to bound.toJvmType(classpath, index))
}

private fun JvmBoundWildcard.JvmUpperBoundWildcard.toJvmTypeArgument(index: Int, classpath: JcClasspath): JvmTypeArgument {
    require(index >= 0)

    return Wildcard(Extends to bound.toJvmType(classpath, index))
}

private fun JvmBoundWildcard.JvmLowerBoundWildcard.toJvmType(index: Int, classpath: JcClasspath): JvmType {
    require(index >= 0)

//    return Wildcard(Super to bound.toJvmType(classpath, index))
    TODO()
}

private fun JvmBoundWildcard.JvmUpperBoundWildcard.toJvmType(index: Int, classpath: JcClasspath): JvmType {
    require(index >= 0)

    return bound.toJvmType(classpath, index)
//    return Wildcard(Extends to bound.toJvmType(classpath, index))
}

private fun JvmTypeVariable.toJvmType(index: Int, classpath: JcClasspath): JvmType {
    return declaration?.toJvmType(index, classpath) ?: Var(symbol, index, classpath.objectClass.toJvmType(classpath), null)
}
