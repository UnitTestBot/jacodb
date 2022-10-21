package org.utbot.jcdb.impl.bytecode

import org.utbot.jcdb.api.ClassSource
import org.utbot.jcdb.api.JcAnnotation
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.findMethodOrNull
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.fs.fullAsmNode
import org.utbot.jcdb.impl.fs.info
import org.utbot.jcdb.impl.suspendableLazy

class JcClassOrInterfaceImpl(
    override val classpath: JcClasspath,
    private val classSource: ClassSource
) : JcClassOrInterface {

    private val info = classSource.info

    override val declaration = JcDeclarationImpl.of(location = classSource.location.jcLocation, this)

    override val name: String get() = classSource.className
    override val simpleName: String get() = classSource.className.substringAfterLast(".")

    override val signature: String?
        get() = info.signature

    override val annotations: List<JcAnnotation>
        get() = info.annotations.map { JcAnnotationImpl(it, classpath) }

    private val lazyInterfaces = suspendableLazy {
        info.interfaces.map {
            classpath.findAndWrap(it) ?: it.throwClassNotFound()
        }
    }

    private val lazySuperclass = suspendableLazy {
        val superClass = info.superClass
        if (superClass != null) {
            classpath.findAndWrap(info.superClass) ?: superClass.throwClassNotFound()
        } else {
            null
        }
    }

    private val lazyOuterClass = suspendableLazy {
        val className = info.outerClass?.className
        if (className != null) {
            classpath.findAndWrap(className) ?: className.throwClassNotFound()
        } else {
            null
        }
    }

    private val lazyInnerClasses = suspendableLazy {
        info.innerClasses.map {
            classpath.findAndWrap(it) ?: it.throwClassNotFound()
        }
    }

    override val access: Int
        get() = info.access

    override suspend fun bytecode() = classSource.fullAsmNode

    override suspend fun outerClass() = lazyOuterClass()

    override val isAnonymous: Boolean
        get() {
            val outerClass = info.outerClass
            return outerClass != null && outerClass.name == null
        }

    override suspend fun outerMethod(): JcMethod? {
        val info = info
        if (info.outerMethod != null && info.outerMethodDesc != null) {
            return outerClass()?.findMethodOrNull(info.outerMethod, info.outerMethodDesc)
        }
        return null
    }

    override val fields: List<JcField>
        get() = info.fields.map { JcFieldImpl(this, it) }

    override val methods: List<JcMethod>
        get() = info.methods.map { toJcMethod(it, classSource) }



    override suspend fun innerClasses() = lazyInnerClasses()

    override suspend fun superclass() = lazySuperclass()

    override suspend fun interfaces() = lazyInterfaces()

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