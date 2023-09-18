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

package org.jacodb.testing.types

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.ext.cfg.fieldRef
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.packageName
import org.jacodb.api.ext.toType
import org.jacodb.impl.bytecode.JcDatabaseClassWriter
import org.jacodb.impl.types.substition.IgnoreSubstitutionProblems
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.CheckClassAdapter
import java.nio.file.Files

class IgnoreSubstitutionProblemsTest : BaseTest() {

    companion object : WithDB(IgnoreSubstitutionProblems)

    private val target = Files.createTempDirectory("jcdb-temp")

    @Test
    fun `should work when params number miss match`() {
        val modifiedType = tweakClass {
            signature = "<K:Ljava/lang/Object;V:Ljava/lang/Object;>Ljava/lang/Object;"
        }.toType()
        modifiedType.methods.forEach {
            it.parameters
            it.typeParameters
            it.returnType
            it.method.instList.forEach {
                it.fieldRef?.field?.fieldType
            }
        }
    }

    private fun tweakClass(action: ClassNode.() -> Unit): JcClassOrInterface {
        cp.findClass("GenericsApi").tweakClass(action)
        cp.findClass("GenericsApiConsumer").tweakClass()
        runBlocking {
            cp.db.load(target.toFile())
        }
        return runBlocking { db.classpath(listOf(target.toFile()), listOf(IgnoreSubstitutionProblems)).findClass("GenericsApiConsumer") }
    }

    private fun JcClassOrInterface.tweakClass(action: ClassNode.() -> Unit = {}) {
        val classNode = asmNode()
        classNode.action()
        val cw = JcDatabaseClassWriter(cp, ClassWriter.COMPUTE_FRAMES)
        val checker = CheckClassAdapter(cw)
        classNode.accept(checker)
        val targetDir = target.resolve(packageName.replace('.', '/'))
        val targetFile = targetDir.resolve("${simpleName}.class").toFile().also {
            it.parentFile?.mkdirs()
        }
        targetFile.writeBytes(cw.toByteArray())
    }

}