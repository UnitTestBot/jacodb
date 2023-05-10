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
import kotlin.Array

enum class JvmWildcardPolarity {
    Extends,
    Super;
}

sealed class JvmTypeArgument

data class Type(val type: JvmType) : JvmTypeArgument()
data class Wildcard(val bound: Pair<JvmWildcardPolarity, JvmType>?) : JvmTypeArgument()

sealed class JvmType

data class Array(val elementType: JvmType) : JvmType()
//data class Class(val cname: String, val typeParameters: Array<out JvmTypeArgument>) : JvmType()
//data class Interface(val iname: String, val typeParameters: Array<out JvmTypeArgument>) : JvmType()
data class Class(val cname: String, val typeParameters: Array<out JvmType>) : JvmType()
data class Interface(val iname: String, val typeParameters: Array<out JvmType>) : JvmType()
data class Var(val id: String, val index: Int, val upb: JvmType, val lwb: JvmType?) : JvmType()
object Null : JvmType() {
    override fun toString(): String = "Null"
}
data class Intersect(val types: Array<out JvmType>) : JvmType()

sealed class JvmDeclaration

data class ClassDeclaration(val cname: String, val params: Array<out JvmType>, val `super`: JvmType?, val supers: Array<out JvmType>) : JvmDeclaration()
data class InterfaceDeclaration(val iname: String, val iparams: Array<out JvmType>, val isupers: Array<out JvmType>) : JvmDeclaration()

data class ClassesTable(val table: Array<out JvmDeclaration>)

fun JcClassOrInterface.toJvmDeclaration(classpath: JcClasspath): JvmDeclaration {
    if ("java.lang.constant.ClassDesc" in this.name) {
        println("")
    }

    val type = toType()

    val typeParams = typeParameters.mapIndexed { index, param -> param.toJvmType(index, classpath) }.toTypedArray()
    val supers = type.interfaces.map { it.toJvmType(classpath) }.toTypedArray()

    return when {
        isInterface -> InterfaceDeclaration(name, typeParams, supers)
        else -> ClassDeclaration(name, typeParams, type.superType?.toJvmType(classpath), supers)
    }
}

fun JvmTypeParameterDeclaration.toJvmType(index: Int, classpath: JcClasspath): Var {
    val typeBounds = bounds

    if (typeBounds.isNullOrEmpty()) {
        return Var(symbol, index, classpath.objectClass.toJvmType(classpath), null)
    }

    return typeBounds.single().toJvmType(classpath).let {
        Var(symbol, index, it, null)
    }
}

fun JcClassType.toJvmType(classpath: JcClasspath): JvmType {
//    val typeParams = typeParameters.map { it.toJvmTypeArgument(classpath) }.toTypedArray()
    val typeParams = typeArguments.mapIndexed { index, param -> param.toJvmType(index, classpath) }.toTypedArray()

    return if (isInterface) Interface(typeName, typeParams) else Class(typeName, typeParams)
}

private fun JcRefType.toJvmType(index: Int, classpath: JcClasspath): JvmType {
    TODO("Not yet implemented")
}

private fun JvmTypeParameterDeclaration.toJvmTypeArgument(classpath: JcClasspath): JvmTypeArgument {
    val typeBounds = bounds
    require(typeBounds != null) {
        "Type bounds should not be null for $this"
    }

    require(typeBounds.isNotEmpty()) {
        "Type bounds should not be empty for $this"
    }

    return typeBounds.singleOrNull()?.toJvmTypeArgument(classpath) ?: classpath.objectClass.toJvmTypeArgument(classpath)
}

private fun JcClassOrInterface.toJvmTypeArgument(classpath: JcClasspath): JvmTypeArgument =
    Type(classpath.findClass(name).toJvmType(classpath))

private fun org.jacodb.impl.types.signature.JvmType.toJvmTypeArgument(classpath: JcClasspath): JvmTypeArgument = when (this) {
    is JvmArrayType -> Type(this.toJvmType(classpath))
    is JvmClassRefType -> Type(classpath.findClass(name).toJvmType(classpath))
    is JvmParameterizedType -> Type(toJvmType(classpath))
    is JvmPrimitiveType -> TODO()
    is JvmTypeVariable -> TODO()
    is JvmParameterizedType.JvmNestedType -> TODO()
    is JvmBoundWildcard.JvmLowerBoundWildcard -> Wildcard(JvmWildcardPolarity.Super to bound.toJvmType(classpath))
    is JvmBoundWildcard.JvmUpperBoundWildcard -> Wildcard(JvmWildcardPolarity.Extends to bound.toJvmType(classpath))
    JvmUnboundWildcard -> Wildcard(null)
}

private fun org.jacodb.impl.types.signature.JvmType.toJvmType(classpath: JcClasspath): JvmType = when (this) {
    is JvmArrayType -> Array(elementType.toJvmType(classpath))
    is JvmClassRefType -> classpath.findClass(name).toJvmType(classpath)
//    is JvmParameterizedType -> Class(name, parameterTypes.map { it.toJvmTypeArgument(classpath) }.toTypedArray())
    is JvmParameterizedType -> Class(name, parameterTypes.mapIndexed { index, param -> param.toJvmType(index, classpath) }.toTypedArray())
    is JvmTypeVariable -> toJvmType(-1 /*It is already mentioned variable with an unknown index*/, classpath)
    is JvmParameterizedType.JvmNestedType -> Class(name, parameterTypes.mapIndexed { index, param -> param.toJvmType(index, classpath) }.toTypedArray())
    is JvmBoundWildcard.JvmLowerBoundWildcard -> TODO()
    is JvmBoundWildcard.JvmUpperBoundWildcard -> TODO()
    is JvmPrimitiveType -> TODO()
    JvmUnboundWildcard -> TODO()
}

private fun org.jacodb.impl.types.signature.JvmType.toJvmType(index: Int, classpath: JcClasspath): JvmType {

    return when (this) {
        is JvmArrayType -> TODO()
        is JvmClassRefType -> TODO()
        is JvmParameterizedType.JvmNestedType -> TODO()
        is JvmParameterizedType -> TODO()
        is JvmPrimitiveType -> TODO()
        is JvmTypeVariable -> toJvmType(index, classpath)
        is JvmBoundWildcard.JvmLowerBoundWildcard -> toJvmType(index, classpath)
        is JvmBoundWildcard.JvmUpperBoundWildcard -> toJvmType(index, classpath)
        JvmUnboundWildcard -> TODO()
    }
}

private fun JvmBoundWildcard.JvmLowerBoundWildcard.toJvmType(index: Int, classpath: JcClasspath): JvmType {
    TODO()
}

private fun JvmBoundWildcard.JvmUpperBoundWildcard.toJvmType(index: Int, classpath: JcClasspath): JvmType {
    return (bound as JvmTypeVariable).toJvmType(index, classpath)
}

private fun JvmTypeVariable.toJvmType(index: Int, classpath: JcClasspath): JvmType {
    return declaration?.toJvmType(index, classpath) ?: Var(symbol, index, classpath.objectClass.toJvmType(classpath), null)
}
