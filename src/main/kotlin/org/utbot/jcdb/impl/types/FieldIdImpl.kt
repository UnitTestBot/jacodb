package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.FieldId
import org.utbot.jcdb.impl.ClassIdService
import org.utbot.jcdb.impl.signature.FieldResolution
import org.utbot.jcdb.impl.signature.FieldSignature

class FieldIdImpl(
    override val classId: ClassId,
    private val info: FieldInfo,
    private val classIdService: ClassIdService
) : FieldId {

    override val name: String
        get() = info.name

    private val lazyType by lazy(LazyThreadSafetyMode.NONE) {
        classIdService.toClassId(info.type)
    }

    private val lazyAnnotations by lazy(LazyThreadSafetyMode.NONE) {
        info.annotations.mapNotNull { classIdService.toClassId(it.className) }
    }

    override suspend fun signature(): FieldResolution {
        return FieldSignature.extract(info.signature)
    }

    override suspend fun access() = info.access
    override suspend fun type() = lazyType

    override suspend fun annotations() = lazyAnnotations

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