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

@file:JvmName("JcBuilders")
package org.utbot.jacodb.impl.features

import org.utbot.jacodb.api.JcArrayType
import org.utbot.jacodb.api.JcBoundedWildcard
import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcClassType
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.JcType
import org.utbot.jacodb.api.ext.HierarchyExtension
import org.utbot.jacodb.api.ext.toType


class BuildersExtension(private val classpath: JcClasspath, private val hierarchyExtension: HierarchyExtension) {

    fun findBuildMethods(jcClass: JcClassOrInterface, includeSubclasses: Boolean = false): Sequence<JcMethod> {
        val hierarchy = hierarchyExtension.findSubClasses(jcClass, true).toMutableSet().also {
            it.add(jcClass)
        }
        val names = when {
            includeSubclasses -> hierarchy.map { it.name }.toSet()
            else -> setOf(jcClass.name)
        }
        val syncQuery = Builders.syncQuery(classpath, names)
        return syncQuery.mapNotNull { response ->
            val foundClass = classpath.toJcClass(response.source)
            val type = foundClass.toType()
            foundClass.declaredMethods[response.methodOffset].takeIf { method ->
                type.declaredMethods.first { it.method == method }.parameters.all { param ->
                    !param.type.hasReferences(hierarchy)
                }
            }
        }
    }

    private fun JcType.hasReferences(jcClasses: Set<JcClassOrInterface>): Boolean {
        return when (this) {
            is JcClassType -> jcClasses.contains(jcClass) || typeArguments.any { it.hasReferences(jcClasses) }
            is JcBoundedWildcard -> (lowerBounds + upperBounds).any { it.hasReferences(jcClasses) }
            is JcArrayType -> elementType.hasReferences(jcClasses)
            else -> false
        }
    }
}


suspend fun JcClasspath.buildersExtension(): BuildersExtension {
    if (!db.isInstalled(Builders)) {
        throw IllegalStateException("This extension requires `Builders` feature to be installed")
    }
    return BuildersExtension(this, hierarchyExt())
}