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
import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.isAssignable
import org.jacodb.api.ext.toType
import org.jacodb.classtable.extractClassesTable
import org.jacodb.impl.features.HierarchyExtensionImpl
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.typesolver.createGsonBuilder
import org.junit.jupiter.api.Test
import kotlin.Array
import kotlin.io.path.Path

// TODO use InMemoryHierarchy feature?
class BoundedTypeQueries : AbstractTypeQuery() {
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

    protected val classes: List<JcClassOrInterface>
    protected val jcClasspath: JcClasspath
    protected val hierarchy: HierarchyExtensionImpl

    init {
        with(configuration) {
            val classesWithClasspath = extractClassesTable(classpathConfiguration.toClasspath)
            classes = classesWithClasspath.classes
            jcClasspath = classesWithClasspath.classpath
        }

        hierarchy = runBlocking { jcClasspath.hierarchyExt() }
    }

    @Test
    fun writeArtificialWildcardExtendsNumber() {
        val numberClass = classes.single { it.name == "java.lang.Number" }
        artificialWildcardExtends(numberClass)
    }

    @Test
    fun writeArtificialWildcardExtendsComparable() {
        val comparableClass = classes.single { it.name == "java.lang.Comparable" }
        artificialWildcardExtends(comparableClass)
    }

    @Test
    fun writeArtificialWildcardExtendsString() {
        val stringClass = classes.single { it.name == "java.lang.String" }
        artificialWildcardExtends(stringClass)
    }

    private fun artificialWildcardExtends(wildcardClass: JcClassOrInterface) {
        val wildcardType = wildcardClass.toType()
        val wildcard = Wildcard(JvmWildcardPolarity.Extends to wildcardClass.toJvmType(jcClasspath))
        val typeArguments = arrayOf(wildcard)

        fun JcClassOrInterface.toJvmTypeWithTypeArguments(): JvmType {
            return if (isInterface) Interface(name, typeArguments) else Class(name, typeArguments)
        }

        for (upperBoundName in upperBoundNames) {
            val upperBound = classes.single { it.name == upperBoundName }.toJvmTypeWithTypeArguments()
            val expectedAnswers = hierarchy
                .findSubClassesIncluding(upperBoundName, allHierarchy = true)
                .filter {
                    if (it.name == upperBoundName) {
                        return@filter true
                    }

                    val superHierarchy = it
                        .toType()
                        .allSuperHierarchy
                        .distinctBy { it.typeName }

                    val upperBoundType = superHierarchy
                        .single { ancestor -> ancestor.name == upperBoundName }

                    upperBoundType
                        .typeArguments
                        .single()
                        .isAssignable(wildcardType)
                }
                .sortedBy { it.name }
                .map { it.toJvmType(jcClasspath) }
                .toList()

            val query = SingleTypeQueryWithUpperBound(upperBound, expectedAnswers.size, expectedAnswers)
            val json = gson.toJson(query)

            Path("only_type_queries", "single_queries", "extends_${wildcardClass.simpleName.toLowerCase()}_type_variables").let {
                it.toFile().mkdirs()

                Path(it.toString(), "$upperBoundName.json").toFile().bufferedWriter().use { writer ->
                    writer.write(json)
                }
            }
        }
    }
}