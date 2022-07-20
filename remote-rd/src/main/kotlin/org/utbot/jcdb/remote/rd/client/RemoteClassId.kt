package org.utbot.jcdb.remote.rd.client

import org.objectweb.asm.tree.MethodNode
import org.utbot.jcdb.api.*
import org.utbot.jcdb.impl.fs.ByteCodeConverter
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.signature.*
import org.utbot.jcdb.impl.suspendableLazy
import org.utbot.jcdb.impl.types.ClassInfo
import org.utbot.jcdb.impl.types.FieldInfo
import org.utbot.jcdb.impl.types.MethodInfo
import java.net.URL
import java.nio.file.Paths

class RemoteClassId(
    private val locationURL: String,
    private val classInfo: ClassInfo,
    override val classpath: ClasspathSet
) : ClassId, ByteCodeConverter {

    override val name: String
        get() = classInfo.name

    override suspend fun access() = classInfo.access

    override val location: ByteCodeLocation? by lazy(LazyThreadSafetyMode.NONE) {
        Paths.get(URL(locationURL).toURI()).toFile().asByteCodeLocation(isRuntime = false)
    }

    override val simpleName: String
        get() = classInfo.name

    private val lazyInterfaces = suspendableLazy {
        classInfo.interfaces.map {
            classpath.findClassOrNull(it) ?: it.throwClassNotFound()
        }
    }

    private val lazySuperclass = suspendableLazy {
        val superClass = classInfo.superClass
        if (superClass != null) {
            classpath.findClassOrNull(superClass) ?: superClass.throwClassNotFound()
        } else {
            null
        }
    }

    private val lazyOuterClass = suspendableLazy {
        val className = classInfo.outerClass?.className
        if (className != null) {
            classpath.findClassOrNull(className) ?: className.throwClassNotFound()
        } else {
            null
        }
    }

    private val lazyMethods = suspendableLazy {
        classInfo.methods.map {
            RemoteMethodId(this, it, classpath)
        }
    }

    private val lazyInnerClasses = suspendableLazy {
        classInfo.innerClasses.map {
            classpath.findClassOrNull(it) ?: it.throwClassNotFound()
        }
    }

    private val lazyAnnotations = suspendableLazy {
        classInfo.annotations.map {
            classpath.findClassOrNull(it.className) ?: it.className.throwClassNotFound()
        }
    }

    private val lazyFields = suspendableLazy {
        classInfo.fields.map { RemoteFieldId(this, it, classpath) }
    }

    override suspend fun byteCode() = null

    override suspend fun innerClasses() = lazyInnerClasses()

    override suspend fun outerClass() = lazyOuterClass()

    override suspend fun isAnonymous(): Boolean {
        val outerClass = classInfo.outerClass
        return outerClass != null && outerClass.name == null
    }

    override suspend fun signature(): TypeResolution {
        return TypeSignature.of(classInfo.signature, classpath)
    }

    override suspend fun outerMethod(): MethodId? {
        val outerMethod = classInfo.outerMethod
        val outerMethodDesc = classInfo.outerMethodDesc
        if (outerMethod != null && outerMethodDesc != null) {
            return outerClass()?.findMethodOrNull(outerMethod, outerMethodDesc)
        }
        return null

    }

    override suspend fun methods() = lazyMethods()

    override suspend fun superclass() = lazySuperclass()

    override suspend fun interfaces() = lazyInterfaces()

    override suspend fun annotations() = lazyAnnotations()

    override suspend fun fields() = lazyFields()

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is RemoteClassId) {
            return false
        }
        return other.name == name && other.location == location
    }

    override fun hashCode(): Int {
        return 31 * location.hashCode() + name.hashCode()
    }

}

class RemoteMethodId(override val classId: ClassId, private val methodInfo: MethodInfo, val classpath: ClasspathSet) :
    MethodId {

    override val name: String
        get() = methodInfo.name

    private val lazyParameters = suspendableLazy {
        methodInfo.parameters.map {
            classpath.findClassOrNull(it) ?: it.throwClassNotFound()
        }
    }

    private val lazyReturnType = suspendableLazy {
        methodInfo.returnType.let {
            classpath.findClassOrNull(it) ?: it.throwClassNotFound()
        }
    }

    private val lazyAnnotations = suspendableLazy {
        methodInfo.annotations.map {
            val className = it.className
            classpath.findClassOrNull(className) ?: className.throwClassNotFound()
        }
    }

    override suspend fun signature(): MethodResolution {
        return MethodSignature.of(methodInfo.signature, classId.classpath)
    }

    override suspend fun returnType() = lazyReturnType()

    override suspend fun parameters() = lazyParameters()

    override suspend fun annotations() = lazyAnnotations()

    override suspend fun description(): String {
        return methodInfo.desc
    }

    override suspend fun readBody(): MethodNode? {
        TODO("Not yet implemented")
    }

    override suspend fun access() = methodInfo.access

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is RemoteMethodId) {
            return false
        }
        return other.name == name && classId == other.classId && methodInfo.desc == other.methodInfo.desc
    }

    override fun hashCode(): Int {
        return 31 * classId.hashCode() + name.hashCode()
    }

}


class RemoteFieldId(
    override val classId: ClassId,
    private val info: FieldInfo,
    private val classpath: ClasspathSet
) : FieldId {

    override val name: String
        get() = info.name

    private val lazyType = suspendableLazy {
        classpath.findClassOrNull(info.type) ?: info.type.throwClassNotFound()
    }

    private val lazyAnnotations = suspendableLazy {
        info.annotations.map {
            classpath.findClassOrNull(it.className) ?: it.className.throwClassNotFound()
        }
    }

    override suspend fun signature(): FieldResolution {
        return FieldSignature.extract(info.signature, classId.classpath)
    }

    override suspend fun access() = info.access
    override suspend fun type() = lazyType()

    override suspend fun annotations() = lazyAnnotations()

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is RemoteFieldId) {
            return false
        }
        return other.name == name && other.classId == classId
    }

    override fun hashCode(): Int {
        return 31 * classId.hashCode() + name.hashCode()
    }
}