package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.FieldId
import org.utbot.jcdb.api.FieldResolution
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.ClassIdService
import org.utbot.jcdb.impl.signature.FieldSignature
import org.utbot.jcdb.impl.suspendableLazy

class FieldIdImpl(
    override val classId: ClassId,
    private val info: FieldInfo,
    private val classIdService: ClassIdService
) : FieldId {

    override val name: String
        get() = info.name

    private val lazyType = suspendableLazy {
        classIdService.toClassId(info.type)
    }

    private val lazyAnnotations = suspendableLazy {
        info.annotations.map { classIdService.toClassId(it.className) ?: it.className.throwClassNotFound() }
    }

    override suspend fun resolution(): FieldResolution {
        return FieldSignature.extract(info.signature, classId.classpath)
    }

    override suspend fun access() = info.access
    override suspend fun type() = lazyType() ?: info.type.throwClassNotFound()

    override suspend fun annotations() = lazyAnnotations()

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is FieldIdImpl) {
            return false
        }
        return other.name == name && other.classId == classId
    }

    override fun hashCode(): Int {
        return 31 * classId.hashCode() + name.hashCode()
    }
}