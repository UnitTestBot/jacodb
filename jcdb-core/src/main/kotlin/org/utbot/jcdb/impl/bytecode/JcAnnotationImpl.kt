package org.utbot.jcdb.impl.bytecode

import org.utbot.jcdb.api.JcAnnotation
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.enumValues
import org.utbot.jcdb.impl.SuspendableLazy
import org.utbot.jcdb.impl.suspendableLazy
import org.utbot.jcdb.impl.types.AnnotationInfo
import org.utbot.jcdb.impl.types.AnnotationValue
import org.utbot.jcdb.impl.types.AnnotationValueList
import org.utbot.jcdb.impl.types.ClassRef
import org.utbot.jcdb.impl.types.EnumRef
import org.utbot.jcdb.impl.types.PrimitiveValue

class JcAnnotationImpl(
    private val info: AnnotationInfo,
    private val classpath: JcClasspath
) : JcAnnotation {

    private val lazyAnnotationClass = suspendableLazy {
        classpath.findClassOrNull(info.className)
    }

    private val lazyValues: SuspendableLazy<Map<String, Any?>> = suspendableLazy {
        val size = info.values.size
        if (size > 0) {
            info.values.map { it.first to fixValue(it.second) }.toMap()
        } else {
            emptyMap()
        }
    }

    override val visible: Boolean get() = info.visible
    override val name: String get() = info.className

    override suspend fun jcClass() = lazyAnnotationClass()

    override suspend fun values() = lazyValues()

    override fun matches(className: String): Boolean {
        return info.className == className
    }

    private suspend fun fixValue(value: AnnotationValue): Any? {
        return when (value) {
            is PrimitiveValue -> value.value
            is ClassRef -> classpath.findClassOrNull(value.className)
            is EnumRef -> classpath.findClassOrNull(value.className)?.enumValues()
                ?.firstOrNull { it.name == value.enumName }

            is AnnotationInfo -> JcAnnotationImpl(value, classpath)
            is AnnotationValueList -> value.annotations.map { fixValue(it) }
        }
    }
}