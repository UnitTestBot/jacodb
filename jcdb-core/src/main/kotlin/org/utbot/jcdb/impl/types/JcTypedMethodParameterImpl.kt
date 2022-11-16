package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcParameter
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.JcTypedMethodParameter
import org.utbot.jcdb.api.PredefinedPrimitive
import org.utbot.jcdb.api.isNullable
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.types.signature.JvmType
import org.utbot.jcdb.impl.types.substition.JcSubstitutor

class JcTypedMethodParameterImpl(
    override val enclosingMethod: JcTypedMethod,
    private val parameter: JcParameter,
    private val jvmType: JvmType?,
    private val substitutor: JcSubstitutor
) : JcTypedMethodParameter {

    val classpath = enclosingMethod.method.enclosingClass.classpath

    override val type: JcType
        get() {
            val typeName = parameter.type.typeName
            val type = jvmType?.let {
                classpath.typeOf(substitutor.substitute(jvmType))
            } ?: classpath.findTypeOrNull(typeName) ?: typeName.throwClassNotFound()

            return if (!parameter.isNullable && type !is PredefinedPrimitive)
                (type as JcRefType).notNullable()
            else
                type
        }

    override val nullable: Boolean
        get() = parameter.isNullable //if (type != null && type.nullable) parameter.isNullable else false

    override val name: String?
        get() = parameter.name
}