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
    private val typeBindings: JcTypeBindings = JcTypeBindings.empty
) : JcTypedField {

    private val resolution = FieldSignature.of(field.signature)
    private val classpath = field.enclosingClass.classpath

    override val name: String get() = this.field.name

    private val fieldTypeGetter = suspendableLazy {
        val typeName = field.type.typeName
        ifSignature {
            typeBindings.toJcRefType(it.fieldType, classpath)
        } ?: classpath.findTypeOrNull(field.type.typeName) ?: typeName.throwClassNotFound()
    }

    override suspend fun fieldType(): JcType = fieldTypeGetter()

    private suspend fun <T> ifSignature(map: suspend (FieldResolutionImpl) -> T?): T? {
        return when (resolution) {
            is FieldResolutionImpl -> map(resolution)
            else -> null
        }
    }

}