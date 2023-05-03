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

package org.jacodb.typesolver

import com.google.gson.*
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.classtable.extractClassesTable
import org.jacodb.testing.allJars
import org.jacodb.typesolver.table.*
import java.io.File
import java.lang.reflect.Type

interface ClassesTableSerializer {
}

class OCanrenClassesTableSerializer {

}

class ClassDeclarationSerializer : JsonSerializer<ClassDeclaration> {
    override fun serialize(src: ClassDeclaration, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val array = JsonArray()
        val prefix = "C"

        array.add(prefix)
        val json = JsonObject()
        with(context) {
            with(json) {
                with(src) {
                    addProperty("cname", cname)
                    add("params", serialize(params))
                    add("super", serialize(`super`))
                    add("supers", serialize(supers))
                }
            }

            array.add(json)
        }

        return array
    }
}

class InterfaceDeclarationSerializer : JsonSerializer<InterfaceDeclaration> {
    override fun serialize(src: InterfaceDeclaration, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val array = JsonArray()
        val prefix = "I"

        array.add(prefix)
        val json = JsonObject()
        with(context) {
            with(json) {
                with(src) {
                    addProperty("iname", iname)
                    add("iparams", serialize(iparams))
                    add("isupers", serialize(isupers))
                }
            }

            array.add(json)
        }

        return array
    }
}

class ArraySerializer : JsonSerializer<org.jacodb.typesolver.table.Array> {
    override fun serialize(src: org.jacodb.typesolver.table.Array, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val array = JsonArray()
        val prefix = "Array"

        array.add(prefix)
        array.add(context.serialize(src.elementType))

        return array
    }
}

class ClassSerializer : JsonSerializer<org.jacodb.typesolver.table.Class> {
    override fun serialize(src: org.jacodb.typesolver.table.Class, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val array = JsonArray()
        val prefix = "Class"

        array.add(prefix)
        array.add(src.cname)
        array.add(context.serialize(src.typeParameters))

        return array
    }
}

class InterfaceSerializer : JsonSerializer<org.jacodb.typesolver.table.Interface> {
    override fun serialize(src: org.jacodb.typesolver.table.Interface, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val array = JsonArray()
        val prefix = "Interface"

        array.add(prefix)
        array.add(src.iname)
        array.add(context.serialize(src.typeParameters))

        return array
    }
}

class VarSerializer : JsonSerializer<org.jacodb.typesolver.table.Var> {
    override fun serialize(src: org.jacodb.typesolver.table.Var, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val array = JsonArray()
        val prefix = "Var"

        array.add(prefix)
        val json = JsonObject()
        with(context) {
            with(json) {
                with(src) {
                    addProperty("id", id)
                    addProperty("index", index)
                    add("upb", serialize(upb))
                    add("lwb", serialize(lwb))
                }
            }

            array.add(json)
        }

        return array
    }
}

class NullSerializer : JsonSerializer<org.jacodb.typesolver.table.Null> {
    override fun serialize(src: org.jacodb.typesolver.table.Null, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val array = JsonArray()
        val prefix = "Null"
        array.add(prefix)

        return array
    }
}

class IntersectSerializer : JsonSerializer<org.jacodb.typesolver.table.Intersect> {
    override fun serialize(src: org.jacodb.typesolver.table.Intersect, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        TODO()
    }
}

class TypeSerializer : JsonSerializer<org.jacodb.typesolver.table.Type> {
    override fun serialize(src: org.jacodb.typesolver.table.Type, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val array = JsonArray()
        val prefix = "Type"
        array.add(prefix)

        array.add(context.serialize(src.type))

        return array
    }
}

class WildcardSerializer : JsonSerializer<org.jacodb.typesolver.table.Wildcard> {
    override fun serialize(src: org.jacodb.typesolver.table.Wildcard, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val array = JsonArray()
        val prefix = "Wildcard"
        array.add(prefix)

        val boundArray = JsonArray()
        src.bound?.let {
            boundArray.add(context.serialize(it.first))
            boundArray.add(context.serialize(it.second))


        }
        array.add(boundArray)

        return array
    }
}

fun createGsonBuilder(): GsonBuilder = GsonBuilder()
    .registerTypeAdapter(ClassDeclaration::class.java, ClassDeclarationSerializer())
    .registerTypeAdapter(InterfaceDeclaration::class.java, InterfaceDeclarationSerializer())
    .registerTypeAdapter(org.jacodb.typesolver.table.Array::class.java, ArraySerializer())
    .registerTypeAdapter(org.jacodb.typesolver.table.Class::class.java, ClassSerializer())
    .registerTypeAdapter(org.jacodb.typesolver.table.Interface::class.java, InterfaceSerializer())
    .registerTypeAdapter(Var::class.java, VarSerializer())
    .registerTypeAdapter(Null::class.java, NullSerializer())
//        .registerTypeAdapter(Intersect::class.java, IntersectSerializer())
    .registerTypeAdapter(org.jacodb.typesolver.table.Type::class.java, TypeSerializer())
    .registerTypeAdapter(org.jacodb.typesolver.table.Wildcard::class.java, WildcardSerializer())
    .setPrettyPrinting()

fun main() {
    val gson = createGsonBuilder().create()
    val (classes, classpath) = extractClassesTable(allJars)
    val classesTable = makeClassesTable(classes, classpath)
    val json = gson.toJson(classesTable)

    File("all_jars.json").bufferedWriter().use {
        it.write(json)
    }
}

fun makeClassesTable(
    classes: List<JcClassOrInterface>,
    classpath: JcClasspath
) = ClassesTable(classes.mapNotNull { runCatching { it.toJvmDeclaration(classpath) }.getOrNull() }.toTypedArray())
