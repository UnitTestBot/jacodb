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

package org.utbot.jcdb.impl.http.resources

import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.utbot.jcdb.api.FieldUsageMode
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.impl.bytecode.JcClassOrInterfaceImpl
import org.utbot.jcdb.impl.types.FieldInfo
import org.utbot.jcdb.impl.types.MethodInfo
import org.utbot.jcdb.impl.types.ParameterInfo
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit


private class LastAccessedClasspath(
    var timestamp: Long,
    val classpath: JcClasspath
) {
    fun tick() {
        timestamp = System.currentTimeMillis()
    }
}

@Tag(
    name = "3. classpaths limited by some jars",
//    description = "$h2 - resource for managing classpaths limited by some number of jars$h2end",
    externalDocs = ExternalDocumentation(url = "$wikiLocation#classpath", description = seeGithub)
)
@RestController
@RequestMapping("/classpaths")
class ClasspathResource(val jcdb: JCDB) : AbstractClasspathResource() {

    private val classpaths = ConcurrentHashMap<String, LastAccessedClasspath>()
    private fun nextUUID() = UUID.randomUUID().toString()

    override fun intervalAction() {
        val current = System.currentTimeMillis()
        val keys = hashSetOf<String>()
        classpaths.entries.forEach { (key, value) ->
            if (value.timestamp < current - TimeUnit.MINUTES.toMillis(10)) {
                keys.add(key)
            }
        }
        keys.forEach {
            classpaths[it]?.classpath?.close()
            classpaths.remove(it)
        }
    }

    @Operation(
        summary = "get all existed classpaths",
        description = "${h3}return all currently available classpaths$h3end"
    )
    @GetMapping("")
    fun all(): List<ClasspathEntity> {
        return classpaths.entries.map {
            ClasspathEntity(it.key)
        }
    }

    @Operation(
        summary = "get classpath by id",
        description = "${h3}get classpath by it's id$h3end"
    )
    @GetMapping("/{classpathId}")
    fun single(
        @Parameter(description = "id of classpath") @PathVariable classpathId: String
    ): ClasspathEntity {
        val classpath = classpathId.findClassPath()
        return ClasspathEntity(
            classpathId,
            locations = classpath.registeredLocations.map {
                LocationEntity(it.id, it.path, it.runtime)
            })
    }

    @Operation(
        summary = "get class by name",
        description = "${h3}get class based on it's name: like `java.lang.String` or `java.util.List`. Primitives, arrays and others are not supported$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#findclassornullname")
    )
    @GetMapping("/{classpathId}/classes/{className}")
    fun findClass(
        @Parameter(description = "id of classpath") @PathVariable classpathId: String,
        @Parameter(
            description = "name of Java class",
            examples = [ExampleObject("java.lang.String"), ExampleObject("java.util.List")]
        ) @PathVariable className: String
    ): ClassEntity {
        return classpathId.findClassPath().findClass(className)
    }

    @Operation(
        summary = "get class bytecode",
        description = "${h3}get class bytecode$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#bytecode")
    )
    @GetMapping("/{classpathId}/classes/{className}/bytecode")
    fun classBytecode(
        @Parameter(description = "id of classpath") @PathVariable classpathId: String,
        @Parameter(
            description = "name of Java class",
            examples = [ExampleObject("java.lang.String"), ExampleObject("java.util.List")]
        )
        @PathVariable className: String
    ): HttpEntity<ByteArray> {
        return classpathId.findClassPath().classBytecode(className)
    }

    @Operation(
        summary = "find sub-classes",
        description = "${h3}return all classes that extends or implements specified class$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#findsubclassesjcclassallhierarchy")
    )
    @GetMapping("/{classpathId}/hierarchies")
    suspend fun findSubclasses(
        @Parameter(description = "id of classpath") @PathVariable classpathId: String,
        @Parameter(description = "number of entities to skip (default is 0)") @RequestParam("skip") optionalSkip: Int?,
        @Parameter(description = "number of entities to take (default is 50)") @RequestParam("take") optionalTop: Int?,
        @Parameter(
            description = "name of Java class",
            examples = [ExampleObject("java.lang.String"), ExampleObject("java.util.List")]
        ) @RequestParam("className") className: String,
        @Parameter(description = "use `true` to get all classes that extends specified") @RequestParam("allHierarchy") allHierarchy: Boolean?
    ): ClassRefPaginator {
        return classpathId.findClassPath().findSubclasses(optionalSkip, optionalTop, className, allHierarchy)
    }

    @Operation(
        summary = "find field usages",
        description = "${h3}find usages of class fields$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#findusagesfield-mode")
    )
    @GetMapping("/{classpathId}/usages/fields")
    suspend fun findFieldUsages(
        @PathVariable classpathId: String,
        @Parameter(description = "number of entities to skip (default is 0)") @RequestParam("skip") optionalSkip: Int?,
        @Parameter(description = "number of entities to take (default is 50)") @RequestParam("take") optionalTop: Int?,
        @Parameter(
            description = "name of Java class",
            examples = [ExampleObject("java.lang.String"), ExampleObject("java.util.List")]
        ) @RequestParam("className") className: String,
        @Parameter(description = "field name") @RequestParam("name") fieldName: String,
        @Parameter(description = "field usage mode: search for read or write") @RequestParam("mode") mode: FieldUsageMode
    ): MethodRefPaginator {
        return classpathId.findClassPath().findFieldUsages(optionalSkip, optionalTop, className, fieldName, mode)
    }

    @Operation(
        summary = "find method usages",
        description = "${h3}find usages of class methods$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#findusagesmethod")
    )
    @GetMapping("/{classpathId}/usages/methods")
    suspend fun findMethodsUsages(
        @PathVariable classpathId: String,
        @Parameter(description = "number of entities to skip (default is 0)") @RequestParam("skip") optionalSkip: Int?,
        @Parameter(description = "number of entities to take (default is 50)") @RequestParam("take") optionalTop: Int?,
        @Parameter(
            description = "name of Java class",
            examples = [ExampleObject("java.lang.String"), ExampleObject("java.util.List")]
        ) @RequestParam("className") className: String,
        @Parameter(description = "method name") @RequestParam("name") methodName: String,
        @Parameter(description = "jvm method description. can be used instead of `offset` param") @RequestParam("description") methodDescription: String?,
        @Parameter(description = "index of method in class. cane be used instead of method `description` param") @RequestParam(
            "offset"
        ) methodOffset: Int?,
    ): MethodRefPaginator {
        return classpathId.findClassPath()
            .findMethodsUsages(optionalSkip, optionalTop, className, methodName, methodDescription, methodOffset)
    }

    @Operation(
        summary = "create new classpath",
        description = "${h3}create new classpath based on locations. If not specified will used only JVM runtime jars$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#classpathdirorjars")
    )
    @PostMapping
    fun new(@RequestBody locations: List<LocationEntity>?): ClasspathEntity {
        val existed = jcdb.locations.associateBy { it.id }
        val classpathLocations = locations.orEmpty().mapNotNull { existed[it.id] }
        val newClasspath = jcdb.classpathOf(classpathLocations)
        val key = nextUUID()
        classpaths[key] = LastAccessedClasspath(System.currentTimeMillis(), newClasspath)
        return ClasspathEntity(key)
    }

    @Operation(
        summary = "close classpath",
        description = "${h3}closes respective classpath$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#classpath ")
    )
    @DeleteMapping("/{classpathId}")
    fun delete(@Parameter(description = "id of classpath") @PathVariable classpathId: String): SimpleResponseEntity {
        classpaths[classpathId]?.classpath?.close()
        classpaths.remove(classpathId)
        return SimpleResponseEntity("Classpath removed")
    }

    private fun String.findClassPath(): JcClasspath {
        return classpaths[this]?.also { it.tick() }?.classpath
            ?: throw NotFoundException("Classpath not found by $this")
    }

}

internal fun JcClassOrInterface.toEntity(): ClassEntity {
    val classInfo = (this as JcClassOrInterfaceImpl).info
    return ClassEntity(
        access = classInfo.access,
        declaredFields = classInfo.fields.map { it.toEntity() },
        declaredMethods = classInfo.methods.map { it.toEntity() },
        interfaces = classInfo.interfaces.map { ClassRefEntity(it) },
        superClass = classInfo.superClass?.let { ClassRefEntity(it) },
        outerClass = classInfo.outerClass?.let { ClassRefEntity(it.className) },
        innerClasses = classInfo.innerClasses.map { ClassRefEntity(it) }
    )
}

private fun MethodInfo.toEntity() = MethodEntity(
    name = name,
    access = access,
    returnType = returnClass,
    params = parametersInfo.map { it.toEntity() },
)

private fun FieldInfo.toEntity() = FieldEntity(
    access = access,
    name = name,
    type = type
)

private fun ParameterInfo.toEntity() = MethodParamEntity(
    access = access,
    name = name,
    type = type,
    index = index
)
