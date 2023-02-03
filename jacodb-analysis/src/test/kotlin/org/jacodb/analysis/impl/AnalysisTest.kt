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

import kotlinx.coroutines.runBlocking
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Test
import org.utbot.jacodb.impl.features.InMemoryHierarchy
import org.utbot.jacodb.impl.features.Usages
import org.utbot.jacodb.impl.features.usagesExt

class AnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

    @Test
    fun `analyse something`() = runBlocking {
        val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
        //graph.callees(graph.successors(graph.successors(graph.successors(graph.entryPoint(graph.classpath.findClassOrNull("org.jacodb.analysis.impl.IFDSMainKt").methods.find {it.name.equals("main")}!!).toList().single()).single()).single()).last()!!)
        println(graph.toString())
    }
}