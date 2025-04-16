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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileSignatureDto(
    val projectName: String,
    val fileName: String,
)

@Serializable
data class NamespaceSignatureDto(
    val name: String,
    val declaringFile: FileSignatureDto,
    val declaringNamespace: NamespaceSignatureDto? = null,
)

@Serializable
data class ClassSignatureDto(
    val name: String,
    val declaringFile: FileSignatureDto,
    val declaringNamespace: NamespaceSignatureDto? = null,
)

@Serializable
data class FieldSignatureDto(
    val declaringClass: ClassSignatureDto,
    val name: String,
    val type: TypeDto,
)

@Serializable
@SerialName("MethodSignature")
data class MethodSignatureDto(
    val declaringClass: ClassSignatureDto,
    val name: String,
    val parameters: List<MethodParameterDto>,
    val returnType: TypeDto,
)

@Serializable
data class MethodParameterDto(
    val name: String,
    val type: TypeDto,
    val isOptional: Boolean = false,
    val isRest: Boolean = false,
)

@Serializable
data class LocalSignatureDto(
    val name: String,
    val method: MethodSignatureDto,
)
