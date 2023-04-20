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

import org.jacodb.analysis.DumpableAnalysisResult
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst

interface FlowFunctionInstance {
    val inIds: List<SpaceId>
    fun compute(fact: DomainFact): Collection<DomainFact>
}

interface SpaceId {
    val value: String
}

interface DomainFact {
    val id: SpaceId
}

object ZEROFact : DomainFact {
    override val id = object : SpaceId {
        override val value: String
            get() = "ZERO fact id"
    }

    override fun toString(): String {
        return "[ZERO fact]"
    }
}

interface FlowFunctionsSpace {
    val inIds: List<SpaceId>
    fun obtainStartFacts(startStatement: JcInst): Collection<DomainFact>
    fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance
    fun obtainCallToStartFlowFunction(callStatement: JcInst, callee: JcMethod): FlowFunctionInstance
    fun obtainCallToReturnFlowFunction(callStatement: JcInst, returnSite: JcInst): FlowFunctionInstance
    fun obtainExitToReturnSiteFlowFunction(callStatement: JcInst, returnSite: JcInst, exitStatement: JcInst): FlowFunctionInstance

    val backward: FlowFunctionsSpace
}

interface Analyzer {
    val backward: Analyzer
    val flowFunctions: FlowFunctionsSpace
    fun calculateSources(ifdsResult: IFDSResult): DumpableAnalysisResult
}