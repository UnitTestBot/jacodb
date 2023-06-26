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

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.HierarchyExtension
import org.jacodb.api.ext.allSuperHierarchy
import org.jacodb.classtable.extractClassesTable
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.types.JcClassTypeImpl
import org.jacodb.testing.allJars
import org.jacodb.testing.guavaLib
import org.jacodb.typesolver.createGsonBuilder
import org.jacodb.typesolver.makeClassesTable
import java.io.File
import java.lang.reflect.Type
import kotlin.Array
import kotlin.io.path.Path
import kotlin.random.Random

private typealias Types = Array<out JvmType>

data class OCanrenTypeQuery(
    val table: ClassesTable,
    @SerializedName("upper_bounds") val upperBounds: Types = emptyArray(),
    @SerializedName("lower_bounds") val lowerBounds: Types = emptyArray(),
    @SerializedName("neg_upper_bounds") val negUpperBounds: Types = emptyArray(),
    @SerializedName("neg_lower_bounds") val negLowerBounds: Types = emptyArray(),
)

class ClassesTableSerializer : JsonSerializer<ClassesTable> {
    override fun serialize(src: ClassesTable, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        context.serialize(src.table)
}


private lateinit var random: Random

enum class ClasspathConfiguration {
    ALL_JARS {
        override val toClasspath: List<File> = allJars
    },
    GUAVA_JAR {
        override val toClasspath: List<File> = listOf(guavaLib)
    };

    abstract val toClasspath: List<File>

    override fun toString(): String = name.lowercase()
}

data class Configuration(
    val classpathConfiguration: ClasspathConfiguration,
    val randomSeed: Int = 42,
    val queriesNumber: Int = 10,
)

fun writeTypeQueries(configuration: Configuration) {
    with(configuration) {
        random = Random(randomSeed)
        // TODO use InMemoryHierarchy feature?
        val (classes, jcClasspath) = extractClassesTable(classpathConfiguration.toClasspath)
        val hierarchy = runBlocking { jcClasspath.hierarchyExt() }

        val classesTable = makeClassesTable(classes, jcClasspath)
        val gson = createGsonBuilder()
            .registerTypeAdapter(ClassesTable::class.java, ClassesTableSerializer())
            .create()

        repeat(configuration.queriesNumber) { counter ->
            val type = chooseType(classes)

            val upperBounds = generateUpperBounds(type, hierarchy, jcClasspath)
            val lowerBounds = generateLowerBounds(type, hierarchy, jcClasspath)
            val negUpperBounds = generateNegUpperBounds(type, hierarchy, jcClasspath)
            val negLowerBounds = generateNegLowerBounds(type, hierarchy, jcClasspath)

            val query = OCanrenTypeQuery(classesTable, upperBounds, lowerBounds, negUpperBounds, negLowerBounds)

            Path("type_queries", classpathConfiguration.toString(), randomSeed.toString(), queriesNumber.toString()).let {
                it.toFile().mkdirs()

                Path(it.toString(), "$counter.json").toFile().bufferedWriter().use { writer ->
                    writer.write(gson.toJson(query))
                }
            }
        }
    }
}

fun main() {
    writeTypeQueries(Configuration(ClasspathConfiguration.GUAVA_JAR))
}

fun generateUpperBounds(type: JcClassOrInterface, hierarchy: HierarchyExtension, jcClasspath: JcClasspath): Types {
    val ancestors = type.allSuperHierarchy

    return ancestors
        .takeRandomElements()
        .map { it.toJvmType(jcClasspath) }
        .toTypedArray()
}

fun generateLowerBounds(
    type: JcClassOrInterface,
    hierarchy: HierarchyExtension,
    jcClasspath: JcClasspath,
): Types {
    val inheritors = hierarchy.findSubClasses(type, allHierarchy = true).toSet()

    return inheritors.map { it.toJvmType(jcClasspath) }.toTypedArray()
}

fun generateNegUpperBounds(type: JcClassOrInterface, hierarchy: HierarchyExtension, jcClasspath: JcClasspath): Types =
    generateUpperBounds(type, hierarchy, jcClasspath)

// It seems such bounds cannot appear in Java
fun generateNegLowerBounds(type: JcClassOrInterface, hierarchy: HierarchyExtension, jcClasspath: JcClasspath): Types =
    emptyArray()

private fun chooseType(classes: Collection<JcClassOrInterface>): JcClassOrInterface =
    classes.random(random)

private fun <T> Collection<T>.takeRandomElements(): List<T> = shuffled(random).take(random.nextInt(size))

