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

package org.utbot.jacodb.impl.http.resources

import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.utbot.jacodb.api.FieldUsageMode
import org.utbot.jacodb.api.JCDB
import org.utbot.jacodb.api.JcClasspath
import java.util.*

@Tag(
    name = "2. all bytecode loaded into database",
    externalDocs = ExternalDocumentation(url = "$wikiLocation#classpath", description = seeGithub)
)
@RestController
@RequestMapping("/classpaths/allJars")
class AllClasspathResource(val jcdb: JCDB) : AbstractClasspathResource() {

    @Volatile
    private var allClasspath: JcClasspath = jcdb.allClasspath

    override fun intervalAction() {
        val old = allClasspath
        allClasspath = jcdb.allClasspath
        old.close()
    }

    @Operation(
        summary = "get class by name",
        description = "${h3}get class based on it's name: like `java.lang.String` or `java.util.List`. Primitives, arrays and others are not supported$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#findclassornullname")
    )
    @GetMapping("/classes/{className}")
    fun findClass(
        @Parameter(
            description = "name of Java class",
            examples = [ExampleObject("java.lang.String"), ExampleObject("java.util.List")]
        ) @PathVariable className: String
    ): ClassEntity {
        return allClasspath.findClass(className)
    }

    @Operation(
        summary = "get class bytecode",
        description = "${h3}get class bytecode$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#bytecode")
    )
    @GetMapping("/classes/{className}/bytecode")
    fun classBytecode(
        @Parameter(
            description = "name of Java class",
            examples = [ExampleObject("java.lang.String"), ExampleObject("java.util.List")]
        ) @PathVariable className: String
    ): HttpEntity<ByteArray> {
        return allClasspath.classBytecode(className)
    }

    @Operation(
        summary = "find sub-classes",
        description = "${h3}return all classes that extends or implements specified class$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#findsubclassesjcclassallhierarchy")
    )
    @GetMapping("/hierarchies")
    suspend fun findSubclasses(
        @Parameter(description = "number of entities to skip (default is 0)") @RequestParam("skip") optionalSkip: Int?,
        @Parameter(description = "number of entities to take (default is 50)") @RequestParam("take") optionalTop: Int?,
        @Parameter(
            description = "name of Java class",
            examples = [ExampleObject("java.lang.String"), ExampleObject("java.util.List")]
        ) @RequestParam("className") className: String,
        @RequestParam("allHierarchy") allHierarchy: Boolean?
    ): ClassRefPaginator {
        return allClasspath.findSubclasses(optionalSkip, optionalTop, className, allHierarchy)
    }

    @Operation(
        summary = "find field usages",
        description = "${h3}find usages of class fields$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#findusagesfield-mode")
    )
    @GetMapping("/usages/fields")
    suspend fun findFieldUsages(
        @Parameter(description = "number of entities to skip (default is 0)") @RequestParam("skip") optionalSkip: Int?,
        @Parameter(description = "number of entities to take (default is 50)") @RequestParam("take") optionalTop: Int?,
        @Parameter(
            description = "name of Java class",
            examples = [ExampleObject("java.lang.String"), ExampleObject("java.util.List")]
        ) @RequestParam("className") className: String,
        @Parameter(description = "field name") @RequestParam("name") fieldName: String,
        @Parameter(description = "field usage mode: search for read or write") @RequestParam("mode") mode: FieldUsageMode
    ): MethodRefPaginator {
        return allClasspath.findFieldUsages(optionalSkip, optionalTop, className, fieldName, mode)
    }

    @Operation(
        summary = "find method usages",
        description = "${h3}find usages of class methods$h3end",
        externalDocs = ExternalDocumentation(url = "$wikiLocation#findusagesmethod")
    )
    @GetMapping("/usages/methods")
    suspend fun findMethodsUsages(
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
        return allClasspath.findMethodsUsages(
            optionalSkip,
            optionalTop,
            className,
            methodName,
            methodDescription,
            methodOffset
        )
    }

}

private val JCDB.allClasspath get() = classpathOf(locations)
