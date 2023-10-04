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

package org.jacodb.configuration

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import sun.misc.Unsafe
import java.lang.reflect.Type

internal class JavaClassSerializer : JsonSerializer<Any> {
    override fun serialize(src: Any, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val json = JsonObject()
        json.addProperty("class", src.javaClass.name)
        val fields = src.javaClass.declaredFields

        for (field in fields) {
            field.isAccessible = true

            val fieldValue = field[src]
            if (fieldValue != null && fieldValue !== src) {
                val fieldType = field.genericType
                val fieldJson = context.serialize(fieldValue, fieldType)
                json.add(field.name, fieldJson)
            }
        }

        return json
    }
}

internal class JavaClassDeserializer : JsonDeserializer<Any> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Any {
        val jsonObject = json.asJsonObject
        val className = jsonObject["class"].asString
        val clazz = Class.forName(className)

        clazz.kotlin.objectInstance?.let {
            return it
        }

        val instance = unsafe.allocateInstance(clazz)

        val fields = clazz.declaredFields

        for (field in fields) {
            field.isAccessible = true

            val name = field.name
            val fieldJson = jsonObject[name]

            if (fieldJson != null) {
                val fieldType = field.genericType
                val fieldValue = context.deserialize<Any>(fieldJson, fieldType)
                field[instance] = fieldValue
            }
        }

        return instance
    }

    companion object {
        private val unsafe: Unsafe
            get() {
                val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
                theUnsafe.isAccessible = true
                return theUnsafe[null] as Unsafe
            }
    }
}

internal fun constructGsonSerializer(usePrettyPrinter: Boolean = true): Gson {
    val builder = GsonBuilder()
        .registerTypeHierarchyAdapter(SerializedTaintConfigurationItem::class.java, JavaClassSerializer())
        .registerTypeHierarchyAdapter(Condition::class.java, JavaClassSerializer())
        .registerTypeHierarchyAdapter(Action::class.java, JavaClassSerializer())
        .registerTypeHierarchyAdapter(FunctionMatcher::class.java, JavaClassSerializer())
        .registerTypeHierarchyAdapter(ParameterMatcher::class.java, JavaClassSerializer())
        .registerTypeHierarchyAdapter(TypeMatcher::class.java, JavaClassSerializer())
        .registerTypeHierarchyAdapter(NameMatcher::class.java, JavaClassSerializer())
        .registerTypeHierarchyAdapter(ConstantValue::class.java, JavaClassSerializer())
        .registerTypeHierarchyAdapter(Position::class.java, JavaClassSerializer())
        .registerTypeHierarchyAdapter(TaintMark::class.java, JavaClassSerializer())

    if (usePrettyPrinter) {
        builder.setPrettyPrinting()
    }

    return builder.create()
}

internal fun constructGsonDeserializer(): Gson =
    GsonBuilder()
        .registerTypeHierarchyAdapter(SerializedTaintConfigurationItem::class.java, JavaClassDeserializer())
        .registerTypeHierarchyAdapter(Condition::class.java, JavaClassDeserializer())
        .registerTypeHierarchyAdapter(Action::class.java, JavaClassDeserializer())
        .registerTypeHierarchyAdapter(FunctionMatcher::class.java, JavaClassDeserializer())
        .registerTypeHierarchyAdapter(ParameterMatcher::class.java, JavaClassDeserializer())
        .registerTypeHierarchyAdapter(TypeMatcher::class.java, JavaClassDeserializer())
        .registerTypeHierarchyAdapter(NameMatcher::class.java, JavaClassDeserializer())
        .registerTypeHierarchyAdapter(ConstantValue::class.java, JavaClassDeserializer())
        .registerTypeHierarchyAdapter(Position::class.java, JavaClassDeserializer())
        .registerTypeHierarchyAdapter(TaintMark::class.java, JavaClassDeserializer())
        .create()


fun List<SerializedTaintConfigurationItem>.serialize(usePrettyPrinter: Boolean = true): String =
    constructGsonSerializer(usePrettyPrinter).toJson(this)

fun String.deserialize(): List<SerializedTaintConfigurationItem> {
    val typeToken: TypeToken<List<SerializedTaintConfigurationItem>> =
        object : TypeToken<List<SerializedTaintConfigurationItem>>() {}

    return constructGsonDeserializer().fromJson(this, typeToken.type)
}
