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

package org.jacodb.analysis.impl.custom

import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst

abstract class AbstractFlowAnalysis<T>(override val graph: JcGraph) : FlowAnalysis<T> {

    override fun newEntryFlow(): T = newFlow()

    protected open fun merge(successor: JcInst, income1: T, income2: T, outcome: T) {
        merge(income1, income2, outcome)
    }

    open fun ins(s: JcInst): T? {
        return ins[s]
    }

    protected fun mergeInto(successor: JcInst, input: T, incoming: T) {
        val tmp = newFlow()
        merge(successor, input, incoming, tmp)
        copy(tmp, input)
    }
}
