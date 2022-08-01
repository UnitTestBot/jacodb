package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.MethodParameterId
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.suspendableLazy

class MethodParameterIdImpl(private val info: ParameterInfo, private val classpath: ClasspathSet) :
    MethodParameterId {

    override suspend fun access() = info.access

    override val name: String?
        get() = info.name

    private val lazyType = suspendableLazy {
        classpath.findClassOrNull(info.type) ?: info.type.throwClassNotFound()
    }
    private val lazyAnnotations = suspendableLazy {
        info.annotations?.map {
            AnnotationIdImpl(info = it, classpath)
        }
    }


    override suspend fun type() = lazyType()

    override suspend fun annotations() = lazyAnnotations().orEmpty()
}