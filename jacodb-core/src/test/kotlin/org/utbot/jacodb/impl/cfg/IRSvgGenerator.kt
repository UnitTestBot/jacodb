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

package org.utbot.jacodb.impl.cfg

import kotlinx.coroutines.runBlocking
import org.utbot.jacodb.api.JCDB
import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.ext.findClass
import org.utbot.jacodb.api.ext.methods
import org.utbot.jacodb.impl.JcGraphChecker
import org.utbot.jacodb.impl.allClasspath
import org.utbot.jacodb.impl.jacodb
import java.io.Closeable
import java.io.File

class IRSvgGenerator(private val folder: File) : Closeable {

    private val db: JCDB
    private val cp: JcClasspath

    init {
        if (!folder.exists()) {
            folder.mkdir()
        } else {
            folder.list()?.forEach { File(folder, it).delete() }
        }
        db = runBlocking {
            jacodb {
                loadByteCode(allClasspath)
            }
        }
        cp = runBlocking { db.classpath(allClasspath) }
    }

    fun generate() {
        dumpClass(cp.findClass<org.utbot.jacodb.impl.cfg.IRExamples>())
    }

    private fun dumpClass(klass: JcClassOrInterface) {
        klass.methods.filter { it.enclosingClass == klass }.mapIndexed { index, it ->
            val instructionList = it.instructionList()
            val fixedName = it.name.replace(Regex("[^A-Za-z0-9]"), "")
            val fileName = "${it.enclosingClass.simpleName}-$fixedName-$index.svg"
            val graph = instructionList.graph(it)
            JcGraphChecker(graph).check()
            graph.toFile("dot", false, file = File(folder, "graph-$fileName"))
            graph.blockGraph().toFile("dot", file = File(folder, "block-graph-$fileName"))
        }
    }


    override fun close() {
        cp.close()
        db.close()
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        throw IllegalStateException("Please provide folder for target svgs")
    }
    val folder = args[0]
    IRSvgGenerator(folder = File(folder)).generate()
}