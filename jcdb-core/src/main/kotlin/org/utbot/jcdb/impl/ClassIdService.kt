package org.utbot.jcdb.impl

import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.impl.bytecode.JcClassOrInterfaceImpl
import org.utbot.jcdb.impl.bytecode.JcMethodImpl
import org.utbot.jcdb.impl.types.MethodInfo
import org.utbot.jcdb.impl.vfs.ClassVfsItem

class ClassIdService(internal val cp: JcClasspath) {

    fun toClassId(node: ClassVfsItem?): JcClassOrInterface? {
        node ?: return null
        return node.asClassId()
    }

    private fun ClassVfsItem.asClassId() = JcClassOrInterfaceImpl(cp, this, this@ClassIdService)

    suspend fun toClassId(fullName: String?): JcClassOrInterface? {
        fullName ?: return null
        return cp.findClassOrNull(fullName)
    }

    fun toMethodId(classId: JcClassOrInterface, methodInfo: MethodInfo, node: ClassVfsItem): JcMethod {
        return JcMethodImpl(methodInfo, node, classId, this)
    }


}