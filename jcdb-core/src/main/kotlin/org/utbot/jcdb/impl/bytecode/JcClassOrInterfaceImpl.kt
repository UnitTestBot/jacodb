package org.utbot.jcdb.impl.bytecode

import org.utbot.jcdb.api.JcAnnotation
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcDeclaration
import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.findMethodOrNull
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.ClassIdService
import org.utbot.jcdb.impl.suspendableLazy
import org.utbot.jcdb.impl.vfs.ClassVfsItem

class JcClassOrInterfaceImpl(
    override val classpath: JcClasspath,
    private val node: ClassVfsItem,
    private val classIdService: ClassIdService
) : JcClassOrInterface {

    override val declaration: JcDeclaration
        get() = JcDeclarationImpl.of(location = node.location, this)

    override val name: String get() = node.fullName
    override val simpleName: String get() = node.name
    override val signature: String?
        get() = node.info().signature

    override val annotations: List<JcAnnotation>
        get() = node.info().annotations.map { JcAnnotationImpl(it, classIdService.cp) }

    private val lazyInterfaces = suspendableLazy {
        node.info().interfaces.map {
            classIdService.toClassId(it) ?: it.throwClassNotFound()
        }
    }

    private val lazySuperclass = suspendableLazy {
        val superClass = node.info().superClass
        if (superClass != null) {
            classIdService.toClassId(node.info().superClass) ?: superClass.throwClassNotFound()
        } else {
            null
        }
    }

    private val lazyOuterClass = suspendableLazy {
        val className = node.info().outerClass?.className
        if (className != null) {
            classIdService.toClassId(className) ?: className.throwClassNotFound()
        } else {
            null
        }
    }

    private val lazyInnerClasses = suspendableLazy {
        node.info().innerClasses.map {
            classIdService.toClassId(it) ?: it.throwClassNotFound()
        }
    }

    override val access: Int
        get() = node.info().access

    override suspend fun outerClass() = lazyOuterClass()

    override val isAnonymous: Boolean
        get() {
            val outerClass = node.info().outerClass
            return outerClass != null && outerClass.name == null
        }

    override suspend fun outerMethod(): JcMethod? {
        val info = node.info()
        if (info.outerMethod != null && info.outerMethodDesc != null) {
            return outerClass()?.findMethodOrNull(info.outerMethod, info.outerMethodDesc)
        }
        return null
    }

    override val fields: List<JcField>
        get() = node.info().fields.map { JcFieldImpl(this, it, classIdService) }

    override val methods: List<JcMethod>
        get() = node.info().methods.map { classIdService.toMethodId(this, it, node) }


    override suspend fun innerClasses() = lazyInnerClasses()

    override suspend fun superclass() = lazySuperclass()

    override suspend fun interfaces() = lazyInterfaces()

    suspend fun info() = node.info()

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JcClassOrInterfaceImpl) {
            return false
        }
        return other.name == name && other.declaration == declaration
    }

    override fun hashCode(): Int {
        return 31 * declaration.hashCode() + name.hashCode()
    }
}