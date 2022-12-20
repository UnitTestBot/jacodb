/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.utbot.jacodb.impl.bytecode

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.MethodNode
import org.utbot.jacodb.api.JcClasspath

val ClassNode.hasFrameInfo: Boolean
    get() {
        return methods.any { mn -> mn.instructions.any { it is FrameNode } }
    }


internal val MethodNode.jsrInlined: MethodNode
    get() {
        val temp = JSRInlinerAdapter(null, access, name, desc, signature, exceptions?.toTypedArray())
        this.accept(temp)
        return temp
    }

internal fun ClassNode.computeFrames(classpath: JcClasspath): ClassNode {
    val ba = this.toByteArray(classpath)
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

private fun ClassNode.toByteArray(classpath: JcClasspath): ByteArray {
    this.inlineJsrs()
    val cw = JcDatabaseClassWriter(classpath, ClassWriter.COMPUTE_FRAMES)
    this.accept(cw)
    return cw.toByteArray()
}
