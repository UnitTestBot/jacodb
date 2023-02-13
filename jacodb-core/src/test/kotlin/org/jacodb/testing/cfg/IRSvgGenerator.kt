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

package org.jacodb.testing.cfg

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcDatabase
import org.jacodb.api.ext.findClass
import org.jacodb.impl.cfg.toFile
import org.jacodb.impl.jacodb
import org.jacodb.testing.allClasspath
import java.io.Closeable
import java.io.File

class IRSvgGenerator(private val folder: File) : Closeable {

    private val db: JcDatabase
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
        dumpClass(cp.findClass<IRExamples>())
    }

    private fun dumpClass(klass: JcClassOrInterface) {
        klass.declaredMethods.filter { it.enclosingClass == klass }.mapIndexed { index, it ->
            val fixedName = it.name.replace(Regex("[^A-Za-z0-9]"), "")
            val fileName = "${it.enclosingClass.simpleName}-$fixedName-$index.svg"
            val graph = it.flowGraph()
            JcGraphChecker(it, graph).check()
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