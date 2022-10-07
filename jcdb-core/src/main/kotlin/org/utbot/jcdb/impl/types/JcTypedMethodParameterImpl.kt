package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcParameter
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.JcTypedMethodParameter
import org.utbot.jcdb.api.isNullable
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.signature.SType

class JcTypedMethodParameterImpl(
    override val enclosingMethod: JcTypedMethod,
    private val parameter: JcParameter,
    private val stype: SType?,
    private val bindings: JcTypeBindings
) : JcTypedMethodParameter {

    val classpath = enclosingMethod.method.enclosingClass.classpath

    override suspend fun type(): JcType {
        val st = stype ?: return classpath.findTypeOrNull(parameter.type.typeName) ?: parameter.type.typeName.throwClassNotFound()
        return classpath.typeOf(st.apply(bindings, null), bindings)
    }

    override val nullable: Boolean
        get() = parameter.isNullable //if (type != null && type.nullable) parameter.isNullable else false

    override val name: String?
        get() = parameter.name
}