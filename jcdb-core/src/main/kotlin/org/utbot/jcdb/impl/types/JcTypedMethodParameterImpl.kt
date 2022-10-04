package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcParameter
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.JcTypedMethodParameter
import org.utbot.jcdb.api.isNullable
import org.utbot.jcdb.impl.signature.SType

class JcTypedMethodParameterImpl(
    override val ownerMethod: JcTypedMethod,
    private val parameter: JcParameter,
    private val stype: SType?,
    private val bindings: JcTypeBindings
) : JcTypedMethodParameter {

    override suspend fun type(): JcType {
        val cp = ownerMethod.method.enclosingClass.classpath
        val st = stype ?: return cp.findTypeOrNull(parameter.type.typeName) ?: throw IllegalStateException("")
        return cp.typeOf(st.apply(bindings))
    }

    override val nullable: Boolean
        get() = parameter.isNullable //if (type != null && type.nullable) parameter.isNullable else false

    override val name: String?
        get() = parameter.name
}