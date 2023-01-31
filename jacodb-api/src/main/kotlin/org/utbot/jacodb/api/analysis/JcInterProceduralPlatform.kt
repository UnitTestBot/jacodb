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

package org.utbot.jacodb.api.analysis

import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.cfg.JcGraph
import org.utbot.jacodb.api.cfg.JcInst

interface JcInterProceduralPlatform : JcAnalysisPlatform {

    fun callersOf(method: JcMethod): Sequence<JcInstIdentity>
    fun groupedCallersOf(method: JcMethod): Map<JcMethod, Set<JcInst>>

    fun callInstructionIdsOf(method: JcMethod): Sequence<JcInstIdentity>
    fun callInstructionsOf(method: JcMethod): Sequence<JcInst>

    fun heads(method: JcMethod): List<JcInstIdentity>

    fun isCall(instId: JcInstIdentity): Boolean

    fun isExit(instId: JcInstIdentity): Boolean

    fun isHead(instId: JcInstIdentity): Boolean

    fun toInstruction(instId: JcInstIdentity): JcInst
}

data class JcInstIdentity(val method: JcMethod, val index: Int) {
    constructor(graph: JcGraph, index: Int) : this(graph.method, index)

}