package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.ClassSource
import org.utbot.jcdb.api.RegisteredLocation

val RegisteredLocation.sources: List<ClassSource>
    get() {
        return jcLocation.classes?.map {
            ClassSourceImpl(this, it.key, it.value)
        }.orEmpty()
    }

val RegisteredLocation.lazySources: List<ClassSource>
    get() {
        val classNames = jcLocation.classNames ?: return emptyList()
        if (classNames.any { it.startsWith("java.") }) {
            return sources
        }
        return classNames.map {
            LazyClassSourceImpl(this, it)
        }
    }