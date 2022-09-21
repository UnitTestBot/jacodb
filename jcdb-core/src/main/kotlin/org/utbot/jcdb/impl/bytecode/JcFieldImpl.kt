package org.utbot.jcdb.impl.bytecode

import org.utbot.jcdb.api.JcAnnotation
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcDeclaration
import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.TypeName
import org.utbot.jcdb.impl.suspendableLazy
import org.utbot.jcdb.impl.types.FieldInfo
import org.utbot.jcdb.impl.types.TypeNameImpl

class JcFieldImpl(
    override val jcClass: JcClassOrInterface,
    private val info: FieldInfo
) : JcField {

    override val name: String
        get() = info.name

    override val declaration: JcDeclaration
        get() = JcDeclarationImpl.of(location = jcClass.declaration.location, this)

    private val lazyAnnotations = suspendableLazy {
        info.annotations.map { JcAnnotationImpl(it, jcClass.classpath) }
    }

//    override suspend fun resolution(): FieldResolution {
//        return FieldSignature.extract(info.signature, classId.classpath)
//    }

    override val access: Int
        get() = info.access

    override val type: TypeName
        get() = TypeNameImpl(info.type)

    override val signature: String?
        get() = info.signature

    override val annotations: List<JcAnnotation>
        get() = info.annotations.map { JcAnnotationImpl(it, jcClass.classpath) }


    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JcFieldImpl) {
            return false
        }
        return other.name == name && other.jcClass == jcClass
    }

    override fun hashCode(): Int {
        return 31 * jcClass.hashCode() + name.hashCode()
    }
}