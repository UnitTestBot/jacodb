package org.utbot.jcdb.impl

import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.MethodId
import org.utbot.jcdb.impl.tree.ClassNode
import org.utbot.jcdb.impl.tree.ClasspathClassTree
import org.utbot.jcdb.impl.types.ClassIdImpl
import org.utbot.jcdb.impl.types.MethodIdImpl
import org.utbot.jcdb.impl.types.MethodInfo

class ClassIdService(internal val cp: ClasspathSet, private val classpathClassTree: ClasspathClassTree) {

    fun toClassId(node: ClassNode?): ClassId? {
        node ?: return null
        return node.asClassId()
    }

    private fun ClassNode.asClassId() = ClassIdImpl(cp, this, this@ClassIdService)

    suspend fun toClassId(fullName: String?): ClassId? {
        fullName ?: return null
        return cp.findClassOrNull(fullName)
    }

    fun toMethodId(classId: ClassId, methodInfo: MethodInfo, node: ClassNode): MethodId {
        return MethodIdImpl(methodInfo, node, classId, this)
    }


}