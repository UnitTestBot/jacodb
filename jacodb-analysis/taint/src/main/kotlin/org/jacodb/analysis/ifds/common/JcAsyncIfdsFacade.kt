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

package org.jacodb.analysis.ifds.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import org.jacodb.analysis.ifds.result.Finding
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class JcAsyncIfdsFacade<Fact, F : Finding<JcInst, Fact>>(
    private val ifdsFacade: JcIfdsFacade<Fact, F>,
) : AutoCloseable {
    private val scope = CoroutineScope(Job())

    @JvmName("runAnalysis")
    fun runAnalysis(
        methods: Collection<JcMethod>,
        timeout: Duration = 60.seconds,
    ) = scope.future {
        ifdsFacade.runAnalysis(methods, timeout)
    }

    fun startAnalysis(
        method: JcMethod,
    ) = scope.future {
        ifdsFacade.startAnalysis(method)
    }

    fun awaitAnalysis() = scope.future {
        ifdsFacade.awaitAnalysis()
    }

    fun collectFindings() = scope.future {
        ifdsFacade.collectFindings()
    }

    fun collectComputationData() = scope.future {
        ifdsFacade.collectComputationData()
    }

    override fun close() {
        ifdsFacade.close()
        scope.cancel()
    }
}