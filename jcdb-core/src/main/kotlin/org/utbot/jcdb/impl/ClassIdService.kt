package org.utbot.jcdb.impl

import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.MethodId
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.impl.tree.ClassNode
import org.utbot.jcdb.impl.tree.ClasspathClassTree
import org.utbot.jcdb.impl.types.ArrayClassIdImpl
import org.utbot.jcdb.impl.types.ClassIdImpl
import org.utbot.jcdb.impl.types.MethodIdImpl
import org.utbot.jcdb.impl.types.MethodInfo

class ClassIdService(private val cp: ClasspathSet, private val classpathClassTree: ClasspathClassTree) {

    fun toClassId(node: ClassNode?): ClassId? {
        node ?: return null
        return node.asClassId()
    }

    private fun ClassNode.asClassId() = ClassIdImpl(cp, this, this@ClassIdService)

    fun toClassId(fullName: String?): ClassId? {
        fullName ?: return null
        val predefinedClass = PredefinedPrimitives.of(fullName, cp)
        if (predefinedClass != null) {
            return predefinedClass
        }
        if (fullName.endsWith("[]")) {
            val targetName = fullName.removeSuffix("[]")
            return toClassId(targetName)?.let {
                ArrayClassIdImpl(it)
            }
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