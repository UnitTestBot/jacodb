package org.utbot.jcdb.impl.bytecode

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.MethodNode

val ClassNode.hasFrameInfo: Boolean
    get() {
        var hasInfo = false
        for (mn in methods) {
            hasInfo = hasInfo || mn.instructions.any { it is FrameNode }
        }
        return hasInfo
    }


internal val MethodNode.jsrInlined: MethodNode
    get() {
        val temp = JSRInlinerAdapter(null, access, name, desc, signature, exceptions?.toTypedArray())
        this.accept(temp)
        return temp
    }

internal fun ClassNode.computeFrames(): ClassNode {
    val ba = this.toByteArray()
    return ba.toClassNode()
}

private fun ByteArray.toClassNode(): ClassNode {
    val classReader = ClassReader(this.inputStream())
    val classNode = ClassNode()
    classReader.accept(classNode, 0)
    return classNode
}

internal fun ClassNode.inlineJsrs() {
    this.methods = methods.map { it.jsrInlined }
}

private fun ClassNode.toByteArray(
): ByteArray {
    this.inlineJsrs()
    val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    this.accept(cw)
    return cw.toByteArray()
}
