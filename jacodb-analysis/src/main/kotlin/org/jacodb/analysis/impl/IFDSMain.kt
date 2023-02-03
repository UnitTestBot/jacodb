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

package org.jacodb.analysis.impl

import java.net.URLClassLoader
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

private fun validateInput(args: Array<String>): Boolean {
    if (args.size != 2) {
        println("usage: <path-to-directory-with-classes> <class-fqn-for-classloader>")
        return false
    }

    try {
        val pathString = args.first()
        val path = Paths.get(pathString)
        if (!path.exists() || !path.isDirectory()) {
            println("incorrect path to directory with classes - directory does not exist")
            return false
        }
        val classloader = URLClassLoader.newInstance(arrayOf(path.toUri().toURL()))
        val clazzFqn = args.last()
        classloader.loadClass(clazzFqn)
    } catch (e: Throwable) {
        e.printStackTrace()
        return false
    }

    return true
}

fun main(args: Array<String>) {
    if (validateInput(args))
        mainImpl(args)
}

private fun mainImpl(args: Array<String>) {
    val directoryPathString = args.first()
    val clazzFQN = args.last()

    // 0. jacodb docs
    // 1. create graph
    // 2. test in debug if it works
    // 3. create tests that it really works
    // 4. create simple rider callgraph and validate that it really works as intended
    // 5. look for instructions and etc
    // 6. understand ifds
    // 7. add interface logic on IFDS and design the solution and how it should be working
}