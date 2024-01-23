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

package org.jacodb.analysis.sarif

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.engine.VulnerabilityInstance
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstLocation
import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path

@Serializable
data class SarifMessage(val text: String)

@Serializable
data class SarifArtifactLocation(val uri: String)

@Serializable
data class SarifRegion(val startLine: Int)

@Serializable
data class SarifPhysicalLocation(val artifactLocation: SarifArtifactLocation, val region: SarifRegion)

@Serializable
data class SarifLogicalLocation(val fullyQualifiedName: String)

@Serializable
data class SarifLocation(val physicalLocation: SarifPhysicalLocation, val logicalLocations: List<SarifLogicalLocation>) {
    companion object {
        private val JcMethod.fullyQualifiedName: String
            get() = "${enclosingClass.name}#${name}"

        private val currentPath: Path = Paths.get("").toAbsolutePath()

        fun fromJcInst(inst: JcInst): SarifLocation = SarifLocation(
            physicalLocation = SarifPhysicalLocation(
                SarifArtifactLocation("${currentPath.relativize(Path(inst.location.method.declaration.location.path))}"),
                SarifRegion(inst.location.lineNumber)
            ),
            logicalLocations = listOf(
                SarifLogicalLocation(inst.location.method.fullyQualifiedName)
            )
        )
    }
}

@Serializable
data class SarifDomainFact(val text: String)

@Serializable
data class SarifState(val domainFact: SarifDomainFact)

@Serializable
data class SarifThreadFlowLocation(val location: SarifLocation, val state: SarifState)

@Serializable
data class SarifThreadFlow(val locations: List<SarifThreadFlowLocation>)

@Serializable
data class SarifCodeFlow(val threadFlows: List<SarifThreadFlow>) {
    companion object {
        fun fromJcTrace(trace: List<IfdsVertex<JcMethod, JcInstLocation, JcInst>>): SarifCodeFlow {
            val threadFlow = trace.map {
                SarifThreadFlowLocation(
                    SarifLocation.fromJcInst(it.statement),
                    SarifState(SarifDomainFact(it.domainFact.toString()))
                )
            }
            return SarifCodeFlow(listOf(SarifThreadFlow(threadFlow)))
        }
    }
}

@Serializable
data class SarifResult(
    val ruleId: String,
    val message: SarifMessage,
    val level: SarifSeverityLevel,
    val locations: List<SarifLocation>,
    val codeFlows: List<SarifCodeFlow>
) {
    companion object {
        fun fromJcVulnerabilityInstance(instance: VulnerabilityInstance<JcMethod, JcInstLocation, JcInst>, maxPathsCount: Int): SarifResult = SarifResult(
            instance.vulnerabilityDescription.ruleId,
            instance.vulnerabilityDescription.message,
            instance.vulnerabilityDescription.level,
            listOf(SarifLocation.fromJcInst(instance.traceGraph.sink.statement)),
            instance.traceGraph.getAllTraces().take(maxPathsCount).map { SarifCodeFlow.fromJcTrace(it) }.toList()
        )
    }
}

@Serializable
data class SarifDriver(
    val name: String,
    val version: String,
    val informationUri: String,
)

@Serializable
data class SarifTool(val driver: SarifDriver)

val IfdsTool = SarifTool(
    SarifDriver(
        name = "JaCo-IFDS",
        version = "1.2.0",
        informationUri = "https://github.com/UnitTestBot/jacodb/blob/develop/jacodb-analysis/README.md"
    )
)

@Serializable
data class SarifRun(val tool: SarifTool, val results: List<SarifResult>)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SarifReport(
    val version: String,

    @SerialName("\$schema")
    val schema: String,

    val runs: List<SarifRun>
) {
    fun encodeToStream(stream: OutputStream) {
        json.encodeToStream(this, stream)
    }

    companion object {
        private const val defaultVersion =
            "2.1.0"
        private const val defaultSchema =
            "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json"

        private const val defaultPathsCount: Int = 3

        private val json = Json {
            prettyPrint = true
            encodeDefaults = false
        }

        fun fromJcVulnerabilities(
            vulnerabilities: List<VulnerabilityInstance<JcMethod, JcInstLocation, JcInst>>,
            pathsCount: Int = defaultPathsCount
        ): SarifReport = SarifReport(
            version = defaultVersion,
            schema = defaultSchema,
            runs = listOf(
                SarifRun(
                    IfdsTool,
                    vulnerabilities.map { SarifResult.fromJcVulnerabilityInstance(it, pathsCount) }
                )
            )
        )
    }
}

@Serializable
enum class SarifSeverityLevel {
    @SerialName("error") ERROR,
    @SerialName("warning") WARNING,
    @SerialName("note") NOTE
}

data class VulnerabilityDescription(
    val message: SarifMessage,
    val ruleId: String,
    val level: SarifSeverityLevel = SarifSeverityLevel.WARNING
)