package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.JcTypedMethod

class JcClassTypeImpl(override val jcClass: JcClassOrInterface, override val nullable: Boolean) : JcClassType {

    override val classpath: JcClasspath
        get() = jcClass.classpath

    override val typeName: String
        get() = jcClass.name

    override suspend fun superType(): JcRefType = TODO("Not yet implemented")

    override suspend fun interfaces(): JcRefType = TODO("Not yet implemented")

    override suspend fun outerType(): JcRefType? = TODO("Not yet implemented")

    override suspend fun outerMethod(): JcTypedMethod? = TODO("Not yet implemented")

    override suspend fun innerTypes(): List<JcRefType> = TODO("Not yet implemented")

    override val methods: List<JcTypedMethod>
        get() = TODO("Not yet implemented")
    override val fields: List<JcTypedField>
        get() = TODO("Not yet implemented")

    override fun notNullable() = JcClassTypeImpl(jcClass, false)
}