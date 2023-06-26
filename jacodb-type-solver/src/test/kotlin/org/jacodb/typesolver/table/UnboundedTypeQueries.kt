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

package org.jacodb.typesolver.table

import org.junit.jupiter.api.Test
import kotlin.io.path.Path

// TODO use InMemoryHierarchy feature?
class UnboundedTypeQueries : AbstractTypeQuery() {
    @Test
    fun writeArtificialUnboundedTypeQueries() {
        for (upperBoundName in upperBoundNames) {
            val upperBound = classes.single { it.name == upperBoundName }.toJvmType(jcClasspath)
            val expectedAnswers = hierarchy
                .findSubClasses(upperBoundName, allHierarchy = true)
                .sortedBy { it.name }
                .map { it.toJvmType(jcClasspath) }
                .toList()

            val query = TypeQueryWithUpperBound(upperBound, expectedAnswers.size, expectedAnswers)
            val json = gson.toJson(query)

            Path("only_type_queries", "single_queries", "unbound_type_variables").let {
                it.toFile().mkdirs()

                Path(it.toString(), "$upperBoundName.json").toFile().bufferedWriter().use { writer ->
                    writer.write(json)
                }
            }
        }
    }
}
