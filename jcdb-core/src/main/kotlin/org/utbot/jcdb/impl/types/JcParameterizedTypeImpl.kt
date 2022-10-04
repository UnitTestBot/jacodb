package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcParametrizedType
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcTypeVariableDeclaration
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.JcTypedMethod

class JcParameterizedTypeImpl(
    override val jcClass: JcClassOrInterface,
    override val originParametrization: List<JcTypeVariableDeclaration>,
    override val parametrization: List<JcRefType>,
    override val nullable: Boolean
) : JcParametrizedType {

    override val classpath: JcClasspath
        get() = jcClass.classpath

    override val typeName: String
        get() = "${jcClass.name}<${parametrization.joinToString { it.typeName }}>"

    override val methods: List<JcTypedMethod>
        get() = TODO("Not yet implemented")

    override val fields: List<JcTypedField>
        get() = TODO("Not yet implemented")

    override fun notNullable() = JcParameterizedTypeImpl(jcClass, originParametrization, parametrization, false)

}