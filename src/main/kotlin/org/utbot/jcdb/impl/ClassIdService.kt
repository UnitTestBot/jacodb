package org.utbot.jcdb.impl

import kotlinx.collections.immutable.toImmutableMap
import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.MethodId
import org.utbot.jcdb.impl.tree.ClassNode
import org.utbot.jcdb.impl.tree.ClasspathClassTree
import org.utbot.jcdb.impl.types.ClassIdImpl
import org.utbot.jcdb.impl.types.MethodIdImpl
import org.utbot.jcdb.impl.types.MethodInfo
import org.utbot.jcdb.impl.types.PredefinedPrimitive

class ClassIdService(private val classpathClassTree: ClasspathClassTree) {

    companion object {
        private val predefinedClasses = PredefinedPrimitive.values.associateBy { it.simpleName }.toImmutableMap()
    }

    fun toClassId(node: ClassNode?): ClassId? {
        node ?: return null
        return node.asClassId()
    }

    private fun ClassNode.asClassId() = ClassIdImpl(this, this@ClassIdService)

    fun toClassId(fullName: String?): ClassId? {
        fullName ?: return null
        val predefinedClass = predefinedClasses[fullName]
        if (predefinedClass != null) {
            return predefinedClass
        }
        return toClassId(classpathClassTree.firstClassOrNull(fullName))
    }

    fun toMethodId(classId: ClassId, methodInfo: MethodInfo, node: ClassNode): MethodId {
        return MethodIdImpl(methodInfo, node, classId, this)
    }

    fun toMethodId(node: ClassNode, methodInfo: MethodInfo): MethodId {
        return MethodIdImpl(methodInfo, node, node.asClassId(), this)
    }

}