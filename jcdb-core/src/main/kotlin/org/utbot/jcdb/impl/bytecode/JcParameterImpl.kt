package org.utbot.jcdb.impl.bytecode

import org.utbot.jcdb.api.JcAnnotation
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcDeclaration
import org.utbot.jcdb.api.JcParameter
import org.utbot.jcdb.api.TypeName
import org.utbot.jcdb.impl.suspendableLazy
import org.utbot.jcdb.impl.types.ParameterInfo
import org.utbot.jcdb.impl.types.TypeNameImpl

class JcParameterImpl(private val info: ParameterInfo, private val classpath: JcClasspath) :
    JcParameter {

    override val access: Int
        get() = info.access

    override val name: String?
        get() = info.name

    override val index: Int
        get() = info.index

    override val declaration: JcDeclaration
        get() = TODO("Not yet implemented")

    override val annotations: List<JcAnnotation>
        get() = TODO("Not yet implemented")

    override val type: TypeName
        get() = TypeNameImpl(info.type)

    private val lazyAnnotations = suspendableLazy {
        info.annotations.map {
            JcAnnotationImpl(info = it, classpath)
        }
    }


//    override suspend fun annotations() = lazyAnnotations().orEmpty()
}