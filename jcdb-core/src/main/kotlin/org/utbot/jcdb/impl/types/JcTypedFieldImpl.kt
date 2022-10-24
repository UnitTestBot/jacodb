package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.suspendableLazy
import org.utbot.jcdb.impl.types.signature.FieldResolutionImpl
import org.utbot.jcdb.impl.types.signature.FieldSignature
import org.utbot.jcdb.impl.types.substition.JcSubstitutor

class JcTypedFieldImpl(
    override val enclosingType: JcRefType,
    override val field: JcField,
    private val substitutor: JcSubstitutor
) : JcTypedField {

    private val resolution = suspendableLazy { FieldSignature.of(field) as? FieldResolutionImpl }
    private val classpath = field.enclosingClass.classpath
    private val resolvedType = suspendableLazy { resolution()?.fieldType }

    override val name: String get() = this.field.name

    private val fieldTypeGetter = suspendableLazy {
        val typeName = field.type.typeName
        resolvedType()?.let {
            classpath.typeOf(substitutor.substitute(it))
        } ?: classpath.findTypeOrNull(field.type.typeName) ?: typeName.throwClassNotFound()
    }

    override suspend fun fieldType(): JcType = fieldTypeGetter()

}