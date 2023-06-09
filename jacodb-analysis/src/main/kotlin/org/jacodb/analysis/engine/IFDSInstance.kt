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

import org.jacodb.analysis.points2.Devirtualizer
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.cfg.JcInst

interface IFDSInstance {
    fun addStart(method: JcMethod)

    fun analyze(): Map<JcMethod, IFDSMethodSummary>
}

interface IFDSInstanceProvider {
    fun createInstance(
        graph: ApplicationGraph<JcMethod, JcInst>,
        analyzer: Analyzer,
        devirtualizer: Devirtualizer,
        context: AnalysisContext,
        unitResolver: UnitResolver<*>,
        methods: List<JcMethod>
    ): IFDSInstance
}