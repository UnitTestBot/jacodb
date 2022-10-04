package org.utbot.jcdb.impl.bytecode

import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcDeclaration
import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.JcParameter

class JcDeclarationImpl(override val location: JcByteCodeLocation, override val relativePath: String) : JcDeclaration {

    companion object {
        fun of(location: JcByteCodeLocation, clazz: JcClassOrInterface): JcDeclarationImpl {
            return JcDeclarationImpl(location, clazz.name)
        }

        fun of(location: JcByteCodeLocation, method: JcMethod): JcDeclarationImpl {
            return JcDeclarationImpl(location, "${method.enclosingClass.name}#${method.name}")
        }

        fun of(location: JcByteCodeLocation, field: JcField): JcDeclarationImpl {
            return JcDeclarationImpl(location, "${field.enclosingClass.name}#${field.name}")
        }

        fun of(location: JcByteCodeLocation, param: JcParameter): JcDeclarationImpl {
            return JcDeclarationImpl(location, "${param.method.enclosingClass.name}#${param.name}:${param.index}")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcDeclarationImpl

        if (location != other.location) return false
        if (relativePath != other.relativePath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = location.hashCode()
        result = 31 * result + relativePath.hashCode()
        return result
    }

}