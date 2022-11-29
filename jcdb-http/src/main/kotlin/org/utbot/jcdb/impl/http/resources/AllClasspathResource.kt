package org.utbot.jcdb.impl.http.resources

import org.springframework.http.HttpEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.utbot.jcdb.api.FieldUsageMode
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JcClasspath
import java.util.*

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

    @GetMapping("/classes/{className}")
    fun findClass(@PathVariable className: String): ClassEntity {
        return allClasspath.findClass(className)
    }

    @GetMapping("/classes/{className}/bytecode")
    fun classBytecode(@PathVariable className: String): HttpEntity<ByteArray> {
        return allClasspath.classBytecode(className)
    }

    @GetMapping("/hierarchies")
    suspend fun findSubclasses(
        @RequestParam("skip") optionalSkip: Int?,
        @RequestParam("top") optionalTop: Int?,
        @RequestParam("className") className: String,
        @RequestParam("allHierarchy") allHierarchy: Boolean?
    ): ClassRefPaginator {
        return allClasspath.findSubclasses(optionalSkip, optionalTop, className, allHierarchy)
    }

    @GetMapping("/usages/fields")
    suspend fun findFieldUsages(
        @RequestParam("skip") optionalSkip: Int?,
        @RequestParam("top") optionalTop: Int?,
        @RequestParam("className") className: String,
        @RequestParam("name") fieldName: String,
        @RequestParam("mode") mode: FieldUsageMode
    ): MethodRefPaginator {
        return allClasspath.findFieldUsages(optionalSkip, optionalTop, className, fieldName, mode)
    }

    @GetMapping("/usages/methods")
    suspend fun findMethodsUsages(
        @RequestParam("skip") optionalSkip: Int?,
        @RequestParam("top") optionalTop: Int?,
        @RequestParam("className") className: String,
        @RequestParam("name") methodName: String,
        @RequestParam("description") methodDescription: String?,
        @RequestParam("offset") methodOffset: Int?,
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
