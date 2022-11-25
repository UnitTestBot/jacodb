package org.utbot.jcdb.impl.http.resources

data class JCDBEntity(
    val jvmRuntime: JCDBRuntimeEntity,
    val locations: List<LocationEntity>
)

data class JCDBRuntimeEntity(
    val version: Int,
    val path: String
)

data class LocationEntity(val id: Long, val path: String?, val runtime: Boolean?)

data class SimpleResponseEntity(val message: String)

data class ClasspathEntity(
    val id: String,
    val locations: List<LocationEntity>? = null
)

data class ClassEntity(
    val access: Int,
    val declaredFields: List<FieldEntity>,
    val declaredMethods: List<MethodEntity>,
    val superClass: ClassRefEntity?,
    val interfaces: List<ClassRefEntity>,
    val outerClass: ClassRefEntity?,
    val innerClasses: List<ClassRefEntity>
)

data class ClassRefEntity(val name: String)

data class MethodEntity(
    val access: Int,
    val name: String,
    val returnType: String,
    val params: List<MethodParamEntity>,
//    val exceptions: List<ClassRefEntity>
)

data class MethodParamEntity(
    val access: Int,
    val index: Int,
    val name: String?,
    val type: String
)

data class FieldEntity(
    val access: Int,
    val name: String,
    val type: String
)
