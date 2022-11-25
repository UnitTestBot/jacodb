package org.utbot.jcdb.impl.bytecode

import org.utbot.jcdb.api.ClassSource
import org.utbot.jcdb.api.JcAnnotation
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.api.findMethodOrNull
import org.utbot.jcdb.impl.fs.fullAsmNode
import org.utbot.jcdb.impl.fs.info

class JcClassOrInterfaceImpl(
    override val classpath: JcClasspath,
    private val classSource: ClassSource
) : JcClassOrInterface {

    val info = classSource.info

    override val declaration = JcDeclarationImpl.of(location = classSource.location, this)

    override val name: String get() = classSource.className
    override val simpleName: String get() = classSource.className.substringAfterLast(".")

    override val signature: String?
        get() = info.signature

    override val annotations: List<JcAnnotation>
        get() = info.annotations.map { JcAnnotationImpl(it, classpath) }

    override val interfaces by lazy(LazyThreadSafetyMode.NONE) {
        info.interfaces.map {
            classpath.findClass(it)
        }
    }

    override val superClass by lazy(LazyThreadSafetyMode.NONE) {
        info.superClass?.let {
            classpath.findClass(it)
        }
    }

    override val outerClass by lazy(LazyThreadSafetyMode.NONE) {
        info.outerClass?.className?.let {
            classpath.findClass(it)
        }
    }

    override val innerClasses by lazy(LazyThreadSafetyMode.NONE) {
        info.innerClasses.map {
            classpath.findClass(it)
        }
    }

    override val access: Int
        get() = info.access

    override fun bytecode() = classSource.fullAsmNode
    override fun binaryBytecode(): ByteArray = classSource.byteCode

    override val isAnonymous: Boolean
        get() {
            val outerClass = info.outerClass
            return outerClass != null && outerClass.name == null
        }

    override val outerMethod: JcMethod?
        get() {
            val info = info
            if (info.outerMethod != null && info.outerMethodDesc != null) {
                return outerClass?.findMethodOrNull(info.outerMethod, info.outerMethodDesc)
            }
            return null
        }

    override val declaredFields: List<JcField> by lazy(LazyThreadSafetyMode.NONE) {
        info.fields.map { JcFieldImpl(this, it) }
    }

    override val declaredMethods: List<JcMethod> by lazy(LazyThreadSafetyMode.NONE) {
        info.methods.map { toJcMethod(it, classSource) }
    }

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