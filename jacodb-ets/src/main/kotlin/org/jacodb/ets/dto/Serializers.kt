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

package org.jacodb.ets.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule

internal val stmtModule = SerializersModule {
    polymorphicDefaultDeserializer(StmtDto::class) { RawStmtSerializer }
}

internal val valueModule = SerializersModule {
    polymorphicDefaultDeserializer(ValueDto::class) { RawValueSerializer }
}

internal val typeModule = SerializersModule {
    polymorphicDefaultDeserializer(TypeDto::class) { RawTypeSerializer }
}

internal val dtoModule = SerializersModule {
    include(stmtModule)
    include(valueModule)
    include(typeModule)
}

object PrimitiveLiteralSerializer : KSerializer<PrimitiveLiteralDto> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PrimitiveLiteral", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PrimitiveLiteralDto) {
        require(encoder is JsonEncoder)
        when (value) {
            is PrimitiveLiteralDto.StringLiteral -> encoder.encodeString(value.value)
            is PrimitiveLiteralDto.NumberLiteral -> encoder.encodeDouble(value.value)
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

object RawStmtSerializer : KSerializer<RawStmtDto> {
    private val serializer = RawStmtDto.serializer()

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): RawStmtDto {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement().jsonObject
        val kind = element.getValue("_").jsonPrimitive.content
        val details = element.toMutableMap()
        details.remove("_")
        return RawStmtDto(kind, JsonObject(details))
    }

    override fun serialize(encoder: Encoder, value: RawStmtDto) {
        encoder.encodeSerializableValue(serializer, value)
    }
}

object RawValueSerializer : KSerializer<RawValueDto> {
    private val serializer = RawValueDto.serializer()

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): RawValueDto {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement().jsonObject
        val kind = element.getValue("_").jsonPrimitive.content
        val type = decoder.json.decodeFromJsonElement<TypeDto>(element.getValue("type"))
        val details = element.toMutableMap()
        details.remove("_")
        details.remove("type")
        return RawValueDto(kind, JsonObject(details), type)
    }

    override fun serialize(encoder: Encoder, value: RawValueDto) {
        encoder.encodeSerializableValue(serializer, value)
    }
}

object RawTypeSerializer : KSerializer<RawTypeDto> {
    private val serializer = RawTypeDto.serializer()

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): RawTypeDto {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement().jsonObject
        val kind = element.getValue("_").jsonPrimitive.content
        val details = element.toMutableMap()
        details.remove("_")
        return RawTypeDto(kind, JsonObject(details))
    }

    override fun serialize(encoder: Encoder, value: RawTypeDto) {
        encoder.encodeSerializableValue(serializer, value)
    }
}
