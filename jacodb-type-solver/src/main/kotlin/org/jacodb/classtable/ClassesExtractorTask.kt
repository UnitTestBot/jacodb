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

package org.jacodb.classtable

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassProcessingTask
import org.jacodb.api.JcClasspath
import org.jacodb.impl.jacodb
import org.jacodb.testing.allJars
import java.io.File

object ClassesExtractorTask : JcClassProcessingTask {
    private val _classes: MutableList<JcClassOrInterface> = mutableListOf()

    val classes: List<JcClassOrInterface> = _classes

    override fun process(clazz: JcClassOrInterface) {
        _classes += clazz
    }
}

private suspend fun extractClassesTableAsync(classPath: List<File>): Pair<List<JcClassOrInterface>, JcClasspath> {
    val db = jacodb {
        useProcessJavaRuntime()
        loadByteCode(classPath)
    }
    val classpath = db.classpath(classPath)
    classpath.execute(ClassesExtractorTask)

    return ClassesExtractorTask.classes to classpath
}

fun extractClassesTable(classPath: List<File>): Pair<List<JcClassOrInterface>, JcClasspath> = runBlocking {
    extractClassesTableAsync(classPath)
}

