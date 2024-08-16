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

import kotlinx.serialization.Serializable

@Serializable
data class FileSignatureDto(
    val projectName: String,
    val fileName: String,
) {
    override fun toString(): String {
        return "@$projectName/$fileName"
    }
}

@Serializable
data class NamespaceSignatureDto(
    val name: String,
    val declaringFile: FileSignatureDto? = null,
    val declaringNamespace: NamespaceSignatureDto? = null,
) {
    override fun toString(): String {
        return if (declaringNamespace != null) {
            "$declaringNamespace::$name"
        } else if (declaringFile != null) {
            "$name in $declaringFile"
        } else {
            name
        }
    }
}

@Serializable
data class ClassSignatureDto(
    val name: String,
    val declaringFile: FileSignatureDto? = null,
    val declaringNamespace: NamespaceSignatureDto? = null,
) {
    override fun toString(): String {
        return if (declaringNamespace != null) {
            "$declaringNamespace::$name"
        } else if (declaringFile != null) {
            "$name in $declaringFile"
        } else {
            name
        }
    }
}

@Serializable
data class FieldSignatureDto(
    val declaringClass: ClassSignatureDto,
    val name: String,
    val type: TypeDto,
) {
    override fun toString(): String {
        return "$name: $type"
    }
}

@Serializable
data class MethodSignatureDto(
    val declaringClass: ClassSignatureDto,
    val name: String,
    val parameters: List<MethodParameterDto>,
    val returnType: TypeDto,
) {
    override fun toString(): String {
        val params = parameters.joinToString()
        return "$name($params): $returnType"
    }
}

@Serializable
data class MethodParameterDto(
    val name: String,
    val type: TypeDto,
    val isOptional: Boolean = false,
) {
    override fun toString(): String {
        return "$name: $type"
    }
}
