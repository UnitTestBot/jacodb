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

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.HierarchyExtension
import org.jacodb.classtable.extractClassesTable
import org.jacodb.impl.features.HierarchyExtensionImpl
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.types.JcClassTypeImpl
import org.jacodb.typesolver.createGsonBuilder
import org.junit.jupiter.api.BeforeEach
import kotlin.Array

abstract class AbstractTypeQuery {
    protected val configuration: Configuration = Configuration(ClasspathConfiguration.ALL_JARS)
    protected val gson: Gson = createGsonBuilder().create()
    protected val upperBoundNames: Array<String> = arrayOf(
        "java.lang.Iterable",
        "java.util.Collection",
        "java.util.List",
        "java.util.Set",
        "java.util.HashSet",
        "java.util.SortedSet",
        "java.util.NavigableSet"
    )

    protected lateinit var classes: List<JcClassOrInterface>
    protected lateinit var jcClasspath: JcClasspath
    protected lateinit var hierarchy: HierarchyExtensionImpl

    @BeforeEach
    fun setupHierarchy() {
        with(configuration) {
            val classesWithClasspath = extractClassesTable(classpathConfiguration.toClasspath)
            classes = classesWithClasspath.classes
            jcClasspath = classesWithClasspath.classpath
        }

        hierarchy = runBlocking { jcClasspath.hierarchyExt() }
    }

    protected data class SingleTypeQueryWithUpperBound(
        val upperBound: JvmType,
        val expectedAnswersNumber: Int,
        val rawExpectedAnswers: List<JvmType>
    )

    protected data class SequentialTypeQueryWithUpperBound(
        val upperBounds: List<JvmType>,
        val expectedAnswersNumber: Int,
        val rawExpectedAnswers: List<JvmType>
    )
}

internal val JcClassType.allSuperHierarchySequence: Sequence<JcClassTypeImpl>
    get() {
        return sequence {
            superType?.let {
                yield(it)
                yieldAll(it.allSuperHierarchySequence)
            }
            yieldAll(interfaces)
            interfaces.forEach {
                yieldAll(it.allSuperHierarchySequence)
            }
        }.map { it as JcClassTypeImpl }
    }

internal val JcClassType.allSuperHierarchy: Set<JcClassTypeImpl>
    get() = allSuperHierarchySequence.toSet()

internal fun HierarchyExtensionImpl.findSubClassesIncluding(
    name: String,
    allHierarchy: Boolean
): Sequence<JcClassOrInterface> = findSubClasses(name, allHierarchy) + cp.findClassOrNull(name)!!
