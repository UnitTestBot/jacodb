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

@file:JvmName("AnalysisMain")
package org.jacodb.analysis

import kotlinx.serialization.Serializable
import mu.KLogging
import org.jacodb.analysis.engine.MainIfdsUnitManager
import org.jacodb.analysis.engine.IfdsUnitRunner
import org.jacodb.analysis.engine.SummaryStorage
import org.jacodb.analysis.engine.UnitResolver
import org.jacodb.analysis.engine.VulnerabilityInstance
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

internal val logger = object : KLogging() {}.logger

typealias AnalysesOptions = Map<String, String>

@Serializable
data class AnalysisConfig(val analyses: Map<String, AnalysesOptions>)


/**
 * This is the entry point for every analysis.
 * Calling this function will find all vulnerabilities reachable from [methods].
 *
 * @param graph instance of [JcApplicationGraph] that provides mixture of CFG and call graph
 * (called supergraph in RHS95).
 * Usually built by [newApplicationGraphForAnalysis].
 *
 * @param unitResolver instance of [UnitResolver] which splits all methods into groups of methods, called units.
 * Units are analyzed concurrently, one unit will be analyzed with one call to [IfdsUnitRunner.run] method.
 * In general, larger units mean more precise, but also more resource-consuming analysis, so [unitResolver] allows
 * to reach compromise.
 * It is guaranteed that [SummaryStorage] passed to all units is the same, so they can share information through it.
 * However, the order of launching and terminating analysis for units is an implementation detail and may vary even for
 * consecutive calls of this method with same arguments.
 *
 * @param ifdsUnitRunner an [IfdsUnitRunner] instance that will be launched for each unit.
 * This is the main argument that defines the analysis.
 *
 * @param methods the list of method for analysis.
 * Each vulnerability will only be reported if it is reachable from one of these.
 *
 * @param timeoutMillis the maximum time for analysis.
 * Note that this does not include time for precalculations
 * (like searching for reachable methods and splitting them into units) and postcalculations (like restoring traces), so
 * the actual running time of this method may be longer.
 */
fun runAnalysis(
    graph: JcApplicationGraph,
    unitResolver: UnitResolver<*>,
    ifdsUnitRunner: IfdsUnitRunner,
    methods: List<JcMethod>,
    timeoutMillis: Long = Long.MAX_VALUE
): List<VulnerabilityInstance> {
    return MainIfdsUnitManager(graph, unitResolver, ifdsUnitRunner, methods, timeoutMillis).analyze()
}