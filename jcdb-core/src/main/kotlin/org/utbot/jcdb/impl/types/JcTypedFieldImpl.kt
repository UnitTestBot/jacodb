package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.PredefinedPrimitive
import org.utbot.jcdb.api.isNullable
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.types.signature.FieldResolutionImpl
import org.utbot.jcdb.impl.types.signature.FieldSignature
import org.utbot.jcdb.impl.types.substition.JcSubstitutor

class JcTypedFieldImpl(
    override val enclosingType: JcRefType,
    override val field: JcField,
    private val substitutor: JcSubstitutor
) : JcTypedField {

    private val classpath = field.enclosingClass.classpath
    private val resolvedType by lazy(LazyThreadSafetyMode.NONE) {
        val resolution = FieldSignature.of(field) as? FieldResolutionImpl
        resolution?.fieldType
    }

    override val name: String get() = this.field.name

    override val fieldType: JcType by lazy {
        val typeName = field.type.typeName
        val type = resolvedType?.let {
            classpath.typeOf(substitutor.substitute(it))
        } ?: classpath.findTypeOrNull(field.type.typeName) ?: typeName.throwClassNotFound()

        if (!field.isNullable && type !is PredefinedPrimitive)
            (type as JcRefType).notNullable()
        else
            type
    }


}