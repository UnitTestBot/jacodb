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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

@Serializable
data class Ark(
    val name: String,
    val absoluteFilePath: String,
    val projectDir: String,
    val projectName: String,
    val namespaces: List<Namespace>,
    val classes: List<Class>,
    val importInfos: List<ImportInfo>,
    val exportInfos: List<ExportInfo>,
) {
    companion object {
        private val json = Json {
            // classDiscriminator = "_"
            prettyPrint = true
        }

        fun loadFromJson(jsonString: String): Ark {
            return json.decodeFromString(jsonString)
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun loadFromJson(stream: InputStream): Ark {
            return json.decodeFromStream(stream)
        }
    }
}

@Serializable
data class Namespace(
    val name: String,
    val classes: List<Class>,
)

@Serializable
data class Class(
    val name: String,
    val modifiers: List<String>,
    val typeParameters: List<String>,
    val superClassName: String?,
    val implementedInterfaceNames: List<String>,
    val fields: List<Field>,
    val methods: List<Method>,
)

@Serializable
data class Field(
    val name: String,
    val modifiers: List<String>,
    val type: String?,
    val questionToken: Boolean,
    val initializer: Value?,
)

@Serializable
data class Method(
    val name: String,
    val modifiers: List<String>,
    val typeParameters: List<String>,
    val parameters: List<Parameter>,
    val returnType: String,
    val body: List<Stmt>,
)

@Serializable
data class Parameter(
    val name: String,
    val optional: Boolean,
    val type: String,
)

@Serializable
data class ImportInfo(
    val importClauseName: String,
    val importType: String,
    val importFrom: String,
    val nameBeforeAs: String?,
    val clauseType: String,
    val modifiers: List<String>,
    val importFromSignature: FileSignature,
    val importProjectType: String,
    val originTsPosition: LineColPosition,
)

@Serializable
data class ExportInfo(
    val exportClauseName: String,
    val exportClauseType: String,
    val exportFrom: String? = null,
    val nameBeforeAs: String? = null,
    val declaringSignature: String? = null,
    val isDefault: Boolean,
    val importInfo: ImportInfo? = null,
    val modifiers: List<String>,
    val originTsPosition: LineColPosition,
)

// @Serializable
// data class Decorator(
//     val kind: String,
//     val type: String?,
// )

@Serializable
data class FileSignature(
    val projectName: String,
    val fileName: String,
)

@Serializable
data class LineColPosition(
    val line: Int,
    val col: Int,
)
