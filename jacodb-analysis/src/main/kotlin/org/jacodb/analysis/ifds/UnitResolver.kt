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

@file:Suppress("FunctionName")

package org.jacodb.analysis.ifds

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.packageName

interface UnitType

data class MethodUnit(val method: JcMethod) : UnitType {
    override fun toString(): String {
        return "MethodUnit(${method.name})"
    }
}

data class ClassUnit(val clazz: JcClassOrInterface) : UnitType {
    override fun toString(): String {
        return "ClassUnit(${clazz.simpleName})"
    }
}

data class PackageUnit(val packageName: String) : UnitType {
    override fun toString(): String {
        return "PackageUnit($packageName)"
    }
}

object SingletonUnit : UnitType {
    override fun toString(): String = javaClass.simpleName
}

object UnknownUnit : UnitType {
    override fun toString(): String = javaClass.simpleName
}

/**
 * Sets a mapping from a [Method] to abstract domain [UnitType].
 *
 * Therefore, it splits all methods into units, containing one or more method each
 * (unit is a set of methods with same value of [UnitType] returned by [resolve]).
 *
 * To get more info about how it is used in analysis, see [runAnalysis].
 */
fun interface UnitResolver<Method> {
    fun resolve(method: Method): UnitType

    companion object {
        fun getByName(name: String): UnitResolver<JcMethod> = when (name) {
            "method" -> MethodUnitResolver
            "class" -> ClassUnitResolver(false)
            "package" -> PackageUnitResolver
            "singleton" -> SingletonUnitResolver
            else -> error("Unknown unit resolver '$name'")
        }
    }
}

fun interface JcUnitResolver : UnitResolver<JcMethod>

val MethodUnitResolver = JcUnitResolver { method ->
    MethodUnit(method)
}

private val ClassUnitResolverWithNested = JcUnitResolver { method ->
    val clazz = generateSequence(method.enclosingClass) { it.outerClass }.last()
    ClassUnit(clazz)
}
private val ClassUnitResolverWithoutNested = JcUnitResolver { method ->
    val clazz = method.enclosingClass
    ClassUnit(clazz)
}

fun ClassUnitResolver(includeNested: Boolean) =
    if (includeNested) {
        ClassUnitResolverWithNested
    } else {
        ClassUnitResolverWithoutNested
    }

val PackageUnitResolver = JcUnitResolver { method ->
    PackageUnit(method.enclosingClass.packageName)
}

val SingletonUnitResolver = JcUnitResolver {
    SingletonUnit
}
