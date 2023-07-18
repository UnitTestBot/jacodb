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

import org.jacodb.analysis.VulnerabilityInstance

data class CalleeInfo(
    val factsAtCalleeStart: Set<IfdsVertex>,
    val callsiteRealisationsGraph: TaintRealisationsGraph
)

data class IfdsMethodSummary(
    val factsAtExits: Map<IfdsVertex, Set<IfdsVertex>>,
    val crossUnitCallees: Map<IfdsVertex, CalleeInfo>,
    val foundVulnerabilities: List<VulnerabilityInstance> // AnalysisResult
)