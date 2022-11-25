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
data class MethodRefEntity(val name: String, val description: String, val offset: Int)

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

open class Paginator<T>(
    val skip: Int,
    val top: Int,
    val items: List<T>? = null
)

class ClassRefPaginator(skip: Int, top: Int, items: List<ClassRefEntity>) : Paginator<ClassRefEntity>(skip, top, items)
class MethodRefPaginator(skip: Int, top: Int, items: List<MethodRefEntity>) : Paginator<MethodRefEntity>(skip, top, items)

inline fun <reified T, reified P : Paginator<T>> Sequence<T>.toPaginator(skip: Int, top: Int): P {
    val constructor = P::class.java.getDeclaredConstructor(Int::class.java, Int::class.java, List::class.java)
    return constructor.newInstance(skip, top, drop(skip).take(top).toList()) as P
}