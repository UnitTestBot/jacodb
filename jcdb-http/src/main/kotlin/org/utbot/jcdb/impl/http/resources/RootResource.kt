package org.utbot.jcdb.impl.http.resources

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.utbot.jcdb.JCDBSettings
import org.utbot.jcdb.api.JCDB
import java.io.File
import java.util.*


@RestController
class RootResource(val jcdbSettings: JCDBSettings, val jcdb: JCDB) {

    private val downloadFolder = File("downloads").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }

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
                runtime = it.runtime
            )
        }
    )

    @PostMapping("/locations")
    suspend fun handleFileUpload(@RequestParam("file") fileUpload: MultipartFile): ResponseEntity<SimpleResponseEntity> {
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