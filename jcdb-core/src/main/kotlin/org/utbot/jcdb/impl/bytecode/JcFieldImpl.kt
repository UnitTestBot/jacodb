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
    override val enclosingClass: JcClassOrInterface,
    private val info: FieldInfo
) : JcField {

    override val name: String
        get() = info.name

    override val declaration: JcDeclaration
        get() = JcDeclarationImpl.of(location = enclosingClass.declaration.location, this)

    private val lazyAnnotations = suspendableLazy {
        info.annotations.map { JcAnnotationImpl(it, enclosingClass.classpath) }
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
        get() = info.annotations.map { JcAnnotationImpl(it, enclosingClass.classpath) }


    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JcFieldImpl) {
            return false
        }
        return other.name == name && other.enclosingClass == enclosingClass
    }

    override fun hashCode(): Int {
        return 31 * enclosingClass.hashCode() + name.hashCode()
    }
}