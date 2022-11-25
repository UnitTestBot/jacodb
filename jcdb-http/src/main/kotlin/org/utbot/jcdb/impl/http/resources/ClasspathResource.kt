package org.utbot.jcdb.impl.http.resources

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
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
import javax.annotation.PostConstruct


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
class ClasspathResource(val jcdb: JCDB) {

    private val classpaths = ConcurrentHashMap<String, LastAccessedClasspath>()
    private fun nextUUID() = UUID.randomUUID().toString()

    private var job: Job? = null

    @PostConstruct
    fun setup() {
        job = GlobalScope.launch {
            while (true) {
                delay(1_000)
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
        }
    }

    @GetMapping("")
    fun getAll(): List<ClasspathEntity> {
        return classpaths.entries.map {
            ClasspathEntity(it.key)
        }
    }

    @GetMapping("/{classpathId}")
    fun get(@PathVariable classpathId: String): ClasspathEntity {
        val accessedClasspath = classpathId.findClassPath()
        return accessedClasspath.let {
            ClasspathEntity(classpathId,
                locations = it.classpath.registeredLocations.map {
                    LocationEntity(it.id, it.path, it.runtime)
                })
        }
    }

    @GetMapping("/{classpathId}/classes/{className}")
    fun getClass(@PathVariable classpathId: String, @PathVariable className: String): ClassEntity {
        val accessedClasspath = classpathId.findClassPath()
        accessedClasspath.tick()
        val jcClass = accessedClasspath.classpath.findClassOrNull(className)
            ?: throw NotFoundException("Class not found by $className")
        return jcClass.toEntity()
    }

    private fun String.findClassPath() =
        classpaths[this]?.also { it.tick() } ?: throw NotFoundException("Classpath not found by ${this}")

    @PostMapping
    fun new(@RequestParam all: Boolean?, @RequestBody locations: List<LocationEntity>?): ClasspathEntity {
        val classpathLocations = when {
            all == true -> jcdb.locations
            else -> {
                val existed = jcdb.locations.associateBy { it.id }
                locations.orEmpty().mapNotNull { existed[it.id] }
            }
        }
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

}

private fun JcClassOrInterface.toEntity(): ClassEntity {
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
