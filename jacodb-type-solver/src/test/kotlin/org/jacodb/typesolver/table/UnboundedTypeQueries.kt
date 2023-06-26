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

import org.jacodb.api.JcClassOrInterface
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

// TODO use InMemoryHierarchy feature?
class UnboundedTypeQueries : AbstractTypeQuery() {
    @Test
    fun writeArtificialUnboundedTypeQueries() {
        for (upperBoundName in upperBoundNames) {
            val upperBound = classes.single { it.name == upperBoundName }.toJvmType(jcClasspath)
            val expectedAnswers = hierarchy
                .findSubClassesIncluding(upperBoundName, allHierarchy = true)
                .sortedBy { it.name }
                .map { it.toJvmType(jcClasspath) }
                .toList()

            val query = SingleTypeQueryWithUpperBound(upperBound, expectedAnswers.size, expectedAnswers)
            val json = gson.toJson(query)

            Path("only_type_queries", "single_queries", "unbound_type_variables").let {
                it.toFile().mkdirs()

                Path(it.toString(), "$upperBoundName.json").toFile().bufferedWriter().use { writer ->
                    writer.write(json)
                }
            }
        }
    }

    @Test
    fun foo() {
        // TODO soot is missed in the classpath
        val loop = classes.filter { it.name == "soot.toolkits.graph.LoopNestTree" }
        println(loop.joinToString())

        println(jcClasspath.findClassOrNull("soot.toolkits.graph.LoopNestTree"))
    }

    @Test
    fun writeArtificialSequentialUnboundedTypeQueries() {
        val upperBoundGroups = mapOf(
            arrayOf("java.lang.Iterable", "java.util.Collection", "java.util.List", "java.util.ArrayList") to hierarchy.findSubClassesIncluding("java.util.ArrayList", allHierarchy = true).toSortedList(),
            arrayOf("java.lang.Iterable", "java.util.Collection", "java.util.List") to hierarchy.findSubClassesIncluding("java.util.List", allHierarchy = true).toSortedList(),
            arrayOf("java.lang.Iterable", "java.util.List") to hierarchy.findSubClassesIncluding("java.util.List", allHierarchy = true).toSortedList(),
            arrayOf("java.util.Set", "java.lang.Iterable") to hierarchy.findSubClassesIncluding("java.util.Set", allHierarchy = true).toSortedList(),
            arrayOf("java.lang.Iterable", "java.util.Collection", "java.util.Set", "java.util.SortedSet", "java.util.NavigableSet", "java.util.TreeSet") to hierarchy.findSubClassesIncluding("java.util.TreeSet", allHierarchy = true).toSortedList(),
            arrayOf("java.lang.Iterable", "java.util.Collection", "java.util.Set", "java.util.NavigableSet", "java.util.SortedSet") to hierarchy.findSubClassesIncluding("java.util.NavigableSet", allHierarchy = true).toSortedList(),
            arrayOf("java.lang.Iterable", "java.util.Collection", "java.util.Set", "java.util.NavigableSet") to hierarchy.findSubClassesIncluding("java.util.NavigableSet", allHierarchy = true).toSortedList(),
            arrayOf("java.util.Set", "java.util.List") to run {
                val setSubclasses = hierarchy.findSubClassesIncluding("java.util.Set", allHierarchy = true).toSet()
                val listSubclasses = hierarchy.findSubClassesIncluding("java.util.List", allHierarchy = true).toSet()

                setSubclasses.filter { it in listSubclasses }
            }.toSortedList(),
        )

        for ((group, answers) in upperBoundGroups) {
            val testName = group.joinToString(separator = "_", postfix = ".json")
            val bounds = group.map { jcClasspath.findClassOrNull(it)!!.toJvmType(jcClasspath) }

            val query = SequentialTypeQueryWithUpperBound(bounds, answers.size, answers.map { it.toJvmType(jcClasspath) })

            val json = gson.toJson(query)

            Path("only_type_queries", "sequential_queries", "unbound_type_variables").let {
                it.toFile().mkdirs()

                Path(it.toString(), testName).toFile().bufferedWriter().use { writer ->
                    writer.write(json)
                }
            }
        }
    }
}

private fun Sequence<JcClassOrInterface>.toSortedList(): List<JcClassOrInterface> = sortedBy { it.name }.toList()
private fun Iterable<JcClassOrInterface>.toSortedList(): List<JcClassOrInterface> = sortedBy { it.name }.toList()
