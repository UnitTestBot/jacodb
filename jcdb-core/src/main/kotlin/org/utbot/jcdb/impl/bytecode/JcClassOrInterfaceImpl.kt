package org.utbot.jcdb.impl.bytecode

import org.utbot.jcdb.api.JcAnnotation
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcDeclaration
import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.findMethodOrNull
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.findAndWrap
import org.utbot.jcdb.impl.fs.ClassSource
import org.utbot.jcdb.impl.suspendableLazy
import org.utbot.jcdb.impl.toJcMethod

class JcClassOrInterfaceImpl(
    override val classpath: JcClasspath,
    private val classSource: ClassSource
) : JcClassOrInterface {

    override val declaration: JcDeclaration
        get() = JcDeclarationImpl.of(location = classSource.location.jcLocation, this)

    override val name: String get() = classSource.className
    override val simpleName: String get() = classSource.className.substringAfterLast(".")

    override val signature: String?
        get() = classSource.info.signature

    override val annotations: List<JcAnnotation>
        get() = classSource.info.annotations.map { JcAnnotationImpl(it, classpath) }

    private val lazyInterfaces = suspendableLazy {
        classSource.info.interfaces.map {
            classpath.findAndWrap(it) ?: it.throwClassNotFound()
        }
    }

    private val lazySuperclass = suspendableLazy {
        val superClass = classSource.info.superClass
        if (superClass != null) {
            classpath.findAndWrap(classSource.info.superClass) ?: superClass.throwClassNotFound()
        } else {
            null
        }
    }

    private val lazyOuterClass = suspendableLazy {
        val className = classSource.info.outerClass?.className
        if (className != null) {
            classpath.findAndWrap(className) ?: className.throwClassNotFound()
        } else {
            null
        }
    }

    private val lazyInnerClasses = suspendableLazy {
        classSource.info.innerClasses.map {
            classpath.findAndWrap(it) ?: it.throwClassNotFound()
        }
    }

    override val access: Int
        get() = classSource.info.access

    override suspend fun bytecode() = classSource.fullAsmNode

    override suspend fun outerClass() = lazyOuterClass()

    override val isAnonymous: Boolean
        get() {
            val outerClass = classSource.info.outerClass
            return outerClass != null && outerClass.name == null
        }

    override suspend fun outerMethod(): JcMethod? {
        val info = classSource.info
        if (info.outerMethod != null && info.outerMethodDesc != null) {
            return outerClass()?.findMethodOrNull(info.outerMethod, info.outerMethodDesc)
        }
        return null
    }

    override val fields: List<JcField>
        get() = classSource.info.fields.map { JcFieldImpl(this, it) }

    override val methods: List<JcMethod>
        get() = classSource.info.methods.map { toJcMethod(it, classSource) }


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