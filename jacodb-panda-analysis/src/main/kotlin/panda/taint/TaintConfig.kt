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

package org.jacodb.panda.taint

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jacodb.analysis.taint.TaintVulnerability
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.taint.configuration.AnyArgument
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.Position
import org.jacodb.taint.configuration.Result

@Serializable
data class SourceMethodConfig(
    val methodName: String?,
    val markName: String = "TAINT",
    val position: Position = Result,
)

@Serializable
data class CleanerMethodConfig(
    val methodName: String?,
    val markName: String = "TAINT",
    val position: Position = Result,
)

@Serializable
data class SinkMethodConfig(
    val methodName: String?,
    val markName: String = "TAINT",
    val position: Position = if (methodName == "log") AnyArgument else Argument(0),
)

@Serializable
sealed interface TaintBuiltInOption

@Serializable
@SerialName("UNTRUSTED_LOOP_BOUND_SINK_CHECK")
object UntrustedLoopBoundSinkCheck : TaintBuiltInOption

@Serializable
@SerialName("UNTRUSTED_ARRAY_SIZE_SINK_CHECK")
object UntrustedArraySizeSinkCheck : TaintBuiltInOption

@Serializable
@SerialName("UNTRUSTED_INDEX_ARRAY_ACCESS_SINK_CHECK")
object UntrustedIndexArrayAccessSinkCheck : TaintBuiltInOption

@Serializable
data class CaseTaintConfig(
    val sourceMethodConfigs: List<SourceMethodConfig> = listOf(),
    val cleanerMethodConfigs: List<CleanerMethodConfig> = listOf(),
    val sinkMethodConfigs: List<SinkMethodConfig> = listOf(),
    val startMethodNamesForAnalysis: List<String>? = null,
    val builtInOptions: List<TaintBuiltInOption> = listOf(),
)

data class SinkResult(
    val sink: TaintVulnerability<PandaInst>,
    val trace: List<PandaInst>? = null
)