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

package org.jacodb.analysis.engine

import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

interface IfdsInstance {
    fun addStart(method: JcMethod)

    fun addStartFact(method: JcMethod, fact: DomainFact): Boolean

    fun analyze(): Map<JcMethod, IfdsMethodSummary>
}

fun interface IfdsInstanceFactory {
    fun createInstance(
        graph: JcApplicationGraph,//ApplicationGraph<JcMethod, JcInst>,
        context: AnalysisContext,
        unitResolver: UnitResolver<*>,
        unit: Any?
    ): IfdsInstance
}