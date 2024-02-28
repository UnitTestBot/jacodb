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

package org.jacodb.analysis.npe

import org.jacodb.analysis.ifds.UniRunner
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.ifds.UnitType
import org.jacodb.analysis.ifds.UnknownUnit
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.taint.TaintRunner
import org.jacodb.analysis.taint.TaintZeroFact
import org.jacodb.analysis.util.Traits
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.api.common.cfg.CommonInst

private val logger = mu.KotlinLogging.logger {}

class NpeManager<Method, Statement>(
    graph: ApplicationGraph<Method, Statement>,
    traits: Traits<Method, Statement>,
    unitResolver: UnitResolver<Method>,
) : TaintManager<Method, Statement>(graph, traits, unitResolver, useBidiRunner = false)
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    override fun newRunner(
        unit: UnitType,
    ): TaintRunner<Method, Statement> {
        check(unit !in runnerForUnit) { "Runner for $unit already exists" }

        val analyzer = NpeAnalyzer(graph, traits)
        val runner = UniRunner(
            graph = graph,
            analyzer = analyzer,
            manager = this@NpeManager,
            unitResolver = unitResolver,
            unit = unit,
            zeroFact = TaintZeroFact
        )

        runnerForUnit[unit] = runner
        return runner
    }

    override fun addStart(method: Method) {
        logger.info { "Adding start method: $method" }
        val unit = unitResolver.resolve(method)
        if (unit == UnknownUnit) return
        methodsForUnit.getOrPut(unit) { hashSetOf() }.add(method)
        // Note: DO NOT add deps here!
    }
}
