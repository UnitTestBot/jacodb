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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

@Serializable
data class EtsFileDto(
    val signature: FileSignatureDto,
    val namespaces: List<NamespaceDto>,
    val classes: List<ClassDto>,
    val importInfos: List<ImportInfoDto>,
    val exportInfos: List<ExportInfoDto>,
) {
    companion object {
        fun loadFromJson(jsonString: String): EtsFileDto {
            return Json.decodeFromString(jsonString)
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun loadFromJson(stream: InputStream): EtsFileDto {
            return Json.decodeFromStream(stream)
        }
    }
}

@Serializable
data class NamespaceDto(
    val signature: NamespaceSignatureDto,
    val classes: List<ClassDto> = emptyList(),
    val namespaces: List<NamespaceDto> = emptyList(),
)

@Serializable
data class ClassDto(
    val signature: ClassSignatureDto,
    val modifiers: List<ModifierDto>,
    val typeParameters: List<TypeDto>,
    val superClassName: String?,
    val implementedInterfaceNames: List<String>,
    val fields: List<FieldDto>,
    val methods: List<MethodDto>,
)

@Serializable
data class FieldDto(
    val signature: FieldSignatureDto,
    val typeParameters: List<TypeDto>,
    val modifiers: List<ModifierDto>? = null,
    @SerialName("questionToken") val isOptional: Boolean = false, // '?'
    @SerialName("exclamationToken") val isDefinitelyAssigned: Boolean = false, // '!'
)

@Serializable(with = ModifierSerializer::class)
sealed class ModifierDto {
    @Serializable
    data class DecoratorItem(
        val kind: String,
        val content: String? = null,
        val param: String? = null,
    ) : ModifierDto()

    @Serializable
    data class StringItem(val modifier: String) : ModifierDto()
}

@Serializable
data class MethodDto(
    val signature: MethodSignatureDto,
    val modifiers: List<ModifierDto>,
    val typeParameters: List<TypeDto>,
    val body: BodyDto? = null,
)

@Serializable
data class BodyDto(
    val locals: List<LocalDto>,
    val cfg: CfgDto,
)

@Serializable
data class ImportInfoDto(
    val importClauseName: String,
    val importType: String,
    val importFrom: String,
    val nameBeforeAs: String? = null,
    val modifiers: List<ModifierDto>,
    val originTsPosition: LineColPositionDto? = null,
)

@Serializable
data class ExportInfoDto(
    val exportClauseName: String,
    val exportClauseType: Int,
    val exportFrom: String? = null,
    val nameBeforeAs: String? = null,
    val isDefault: Boolean,
    val modifiers: List<ModifierDto>,
    val originTsPosition: LineColPositionDto? = null,
)

@Serializable
data class LineColPositionDto(
    val line: Int,
    val col: Int,
)
