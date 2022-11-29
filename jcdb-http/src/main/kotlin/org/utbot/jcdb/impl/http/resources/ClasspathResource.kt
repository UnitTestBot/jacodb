package org.utbot.jcdb.impl.http.resources

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

    @GetMapping("")
    fun all(): List<ClasspathEntity> {
        return classpaths.entries.map {
            ClasspathEntity(it.key)
        }
    }

    @GetMapping("/{classpathId}")
    fun single(@PathVariable classpathId: String): ClasspathEntity {
        val classpath = classpathId.findClassPath()
        return ClasspathEntity(
            classpathId,
            locations = classpath.registeredLocations.map {
                LocationEntity(it.id, it.path, it.runtime)
            })
    }

    @GetMapping("/{classpathId}/classes/{className}")
    fun findClass(@PathVariable classpathId: String, @PathVariable className: String): ClassEntity {
        return classpathId.findClassPath().findClass(className)
    }

    @GetMapping("/{classpathId}/classes/{className}/bytecode")
    fun classBytecode(@PathVariable classpathId: String, @PathVariable className: String): HttpEntity<ByteArray> {
        return classpathId.findClassPath().classBytecode(className)
    }

    @GetMapping("/{classpathId}/hierarchies")
    suspend fun findSubclasses(
        @PathVariable classpathId: String,
        @RequestParam("skip") optionalSkip: Int?,
        @RequestParam("top") optionalTop: Int?,
        @RequestParam("className") className: String,
        @RequestParam("allHierarchy") allHierarchy: Boolean?
    ): ClassRefPaginator {
        return classpathId.findClassPath().findSubclasses(optionalSkip, optionalTop, className, allHierarchy)
    }

    @GetMapping("/{classpathId}/usages/fields")
    suspend fun findFieldUsages(
        @PathVariable classpathId: String,
        @RequestParam("skip") optionalSkip: Int?,
        @RequestParam("top") optionalTop: Int?,
        @RequestParam("className") className: String,
        @RequestParam("name") fieldName: String,
        @RequestParam("mode") mode: FieldUsageMode
    ): MethodRefPaginator {
        return classpathId.findClassPath().findFieldUsages(optionalSkip, optionalTop, className, fieldName, mode)
    }

    @GetMapping("/{classpathId}/usages/methods")
    suspend fun findMethodsUsages(
        @PathVariable classpathId: String,
        @RequestParam("skip") optionalSkip: Int?,
        @RequestParam("top") optionalTop: Int?,
        @RequestParam("className") className: String,
        @RequestParam("name") methodName: String,
        @RequestParam("description") methodDescription: String?,
        @RequestParam("offset") methodOffset: Int?,
    ): MethodRefPaginator {
        return classpathId.findClassPath()
            .findMethodsUsages(optionalSkip, optionalTop, className, methodName, methodDescription, methodOffset)
    }

    @PostMapping
    fun new(@RequestBody locations: List<LocationEntity>?): ClasspathEntity {
        val existed = jcdb.locations.associateBy { it.id }
        val classpathLocations = locations.orEmpty().mapNotNull { existed[it.id] }
        val newClasspath = jcdb.classpathOf(classpathLocations)
        val key = nextUUID()
        classpaths[key] = LastAccessedClasspath(System.currentTimeMillis(), newClasspath)
        return ClasspathEntity(key)
    }

    @DeleteMapping("/{classpathId}")
    fun delete(@PathVariable classpathId: String): SimpleResponseEntity {
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
