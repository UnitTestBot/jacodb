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

package org.jacodb.impl.cfg.util

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor
import java.io.PrintWriter
import java.io.StringWriter

private val printer = Textifier()
private val mp = TraceMethodVisitor(printer)

fun ClassNode.print() = buildString {
    appendLine("Class $name")
    for (mn in methods) {
        appendLine(mn.print())
    }
}

fun MethodNode.print() = buildString {
    appendLine(name)
    for (insn in instructions) {
        append(insn.print())
    }
    for (insn in tryCatchBlocks) {
        append(insn.print())
    }
}

fun AbstractInsnNode.print(): String {
    this.accept(mp)
    val sw = StringWriter()
    printer.print(PrintWriter(sw))
    printer.getText().clear()
    return sw.toString()
}

fun TryCatchBlockNode.print() = buildString {
    append("${start.print().dropLast(1)} ")
    append("${end.print().dropLast(1)} ")
    append("${handler.print().dropLast(1)} ")
    appendLine(type ?: "java/lang/Throwable")
}
