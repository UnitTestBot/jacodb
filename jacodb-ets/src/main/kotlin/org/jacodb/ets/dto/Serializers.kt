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

@file:OptIn(ExperimentalSerializationApi::class)

package org.jacodb.ets.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull

object ModifierSerializer : KSerializer<ModifierDto> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("Modifier", PolymorphicKind.SEALED) {
            element<ModifierDto.DecoratorItem>("DecoratorItem")
            element<String>("StringItem")
        }

    override fun serialize(encoder: Encoder, value: ModifierDto) {
        require(encoder is JsonEncoder)
        when (value) {
            is ModifierDto.DecoratorItem -> encoder.encodeJsonElement(encoder.json.encodeToJsonElement(value))
            is ModifierDto.StringItem -> encoder.encodeString(value.modifier)
        }
    }

    override fun deserialize(decoder: Decoder): ModifierDto {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonObject -> decoder.json.decodeFromJsonElement<ModifierDto.DecoratorItem>(element)
            element is JsonPrimitive && element.isString -> ModifierDto.StringItem(element.content)
            else -> throw SerializationException("Unsupported modifier: $element")
        }
    }
}

object PrimitiveLiteralSerializer : KSerializer<PrimitiveLiteralDto> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PrimitiveLiteral", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PrimitiveLiteralDto) {
        require(encoder is JsonEncoder)
        when (value) {
            is PrimitiveLiteralDto.StringLiteral -> encoder.encodeString(value.value)
            is PrimitiveLiteralDto.NumberLiteral -> encoder.encodeDouble(value.value.toDouble())
            is PrimitiveLiteralDto.BooleanLiteral -> encoder.encodeBoolean(value.value)
        }
    }

    override fun deserialize(decoder: Decoder): PrimitiveLiteralDto {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        if (element !is JsonPrimitive) {
            throw SerializationException("Expected JsonPrimitive, but found $element")
        }
        if (element.isString) {
            return PrimitiveLiteralDto.StringLiteral(element.content)
        }
        val b = element.booleanOrNull
        if (b != null) {
            return PrimitiveLiteralDto.BooleanLiteral(b)
        } else {
            return PrimitiveLiteralDto.NumberLiteral(element.double)
        }
    }
}
