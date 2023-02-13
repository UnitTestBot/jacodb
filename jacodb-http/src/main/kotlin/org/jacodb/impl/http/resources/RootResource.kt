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

package org.jacodb.impl.http.resources

import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.jacodb.api.JcDatabase
import org.jacodb.impl.JcSettings
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.util.*

const val wikiLocation = "https://github.com/UnitTestBot/jacodb/wiki/Api-reference"
const val h3 = "<h3>"
const val h3end = "</h3>"

const val seeGithub = "see GitHub"

@Tag(
    name = "1. database instance resource",
    externalDocs = ExternalDocumentation(url = "$wikiLocation#database", description = seeGithub)
)
@RestController
class RootResource(val jcdbSettings: JcSettings, val jcdb: JcDatabase) {

    private val downloadFolder = File("downloads").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }

    @Operation(
        summary = "get common information about database",
        description = "${h3}Gets common information about database instance: runtime, processed bytecode locations etc$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#database")
    )
    @GetMapping("/")
    fun databaseEntity() = JCDBEntity(
        jvmRuntime = JCDBRuntimeEntity(
            version = jcdb.runtimeVersion.majorVersion,
            path = jcdbSettings.jre.absolutePath
        ),
        locations = jcdb.locations.map {
            LocationEntity(
                id = it.id,
                path = it.path,
                runtime = it.isRuntime
            )
        }
    )

    @Operation(
        summary = "uploads jar file into database",
        description = "${h3}Uploads new jar-file into database$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#loaddirorjars")
    )
    @PostMapping(
        "/locations",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    suspend fun handleFileUpload(@RequestPart("file") fileUpload: MultipartFile): ResponseEntity<SimpleResponseEntity> {
        val name = fileUpload.originalFilename
        if (name != null && name.endsWith(".jar")) {
            val destination = name.destinationFile()
            destination.outputStream().buffered().use {
                fileUpload.inputStream.copyTo(it)
            }
            jcdb.load(destination)
            return ResponseEntity.ok(SimpleResponseEntity("$name uploaded successfully"))
        }
        return ResponseEntity.badRequest()
            .body(SimpleResponseEntity("Loading $name not supported. Only jars are supported"))
    }

    private fun String.destinationFile(): File {
        var index = 0
        val withoutExtension = removeSuffix(".jar")
        while (index <= 1000) {
            val file = File(downloadFolder, "$withoutExtension-$index.jar")
            if (!file.exists()) {
                return file
            }
            index++
        }
        throw IllegalStateException("Can't find a place for $this all names existed")
    }
}