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

package org.jacodb.panda.dynamic.ark.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray

object ListOfModifiersSerializer : KSerializer<List<ModifierDto>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("modifiers", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: List<ModifierDto>) {
        val output = encoder as JsonEncoder
        output.encodeJsonElement(JsonArray(value.map {
            when (it) {
                is ModifierDto.DecoratorItem -> output.json.encodeToJsonElement(it)
                is ModifierDto.StringItem -> JsonPrimitive(it.value)
            }
        }))
    }

    override fun deserialize(decoder: Decoder): List<ModifierDto> {
        val input = decoder as JsonDecoder
        val jsonArray = input.decodeJsonElement().jsonArray
        val result = mutableListOf<ModifierDto>()
        jsonArray.forEach { jsonElement ->
            if (jsonElement is JsonObject) {
                if (jsonElement.containsKey("kind") && jsonElement.containsKey("content")) {
                    result.add(input.json.decodeFromJsonElement(ModifierDto.DecoratorItem.serializer(), jsonElement))
                }
            } else if (jsonElement is JsonPrimitive && jsonElement.isString) {
                result.add(ModifierDto.StringItem(jsonElement.content))
            } else {
                throw SerializationException("Unsupported modifier type: ${jsonElement::class}")
            }
        }
        return result
    }
}
