package org.utbot.jcdb.impl

import org.utbot.jcdb.api.ClassSource
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.impl.bytecode.JcClassOrInterfaceImpl
import org.utbot.jcdb.impl.bytecode.JcMethodImpl
import org.utbot.jcdb.impl.types.MethodInfo
import org.utbot.jcdb.impl.vfs.ClassVfsItem

fun JcClasspath.toJcClass(item: ClassVfsItem?): JcClassOrInterface? {
    item ?: return null
    return JcClassOrInterfaceImpl(this, item.source)
}

suspend fun JcClasspath.findAndWrap(fullName: String?): JcClassOrInterface? {
    fullName ?: return null
    return findClassOrNull(fullName)
}

fun JcClassOrInterface.toJcMethod(methodInfo: MethodInfo, source: ClassSource): JcMethod {
    return JcMethodImpl(methodInfo, source, this)
}