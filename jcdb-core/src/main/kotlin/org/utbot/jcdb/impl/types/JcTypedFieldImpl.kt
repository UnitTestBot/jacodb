package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.signature.FieldResolutionImpl
import org.utbot.jcdb.impl.signature.FieldSignature
import org.utbot.jcdb.impl.suspendableLazy

class JcTypedFieldImpl(
    override val enclosingType: JcRefType,
    override val field: JcField,
    val typeBindings: JcTypeBindings = JcTypeBindings.empty
) : JcTypedField {

    private val resolution = FieldSignature.of(field.signature) as? FieldResolutionImpl
    private val classpath = field.enclosingClass.classpath
    private val resolvedType = resolution?.fieldType?.apply(typeBindings, null)

    override val name: String get() = this.field.name

    private val fieldTypeGetter = suspendableLazy {
        val typeName = field.type.typeName
        resolvedType?.let { classpath.typeOf(it, typeBindings) }
            ?: classpath.findTypeOrNull(field.type.typeName)
            ?: typeName.throwClassNotFound()
    }

    override suspend fun fieldType(): JcType = fieldTypeGetter()

}