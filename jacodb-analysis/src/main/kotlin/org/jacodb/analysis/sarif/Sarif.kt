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

import io.github.detekt.sarif4k.ArtifactLocation
import io.github.detekt.sarif4k.CodeFlow
import io.github.detekt.sarif4k.Level
import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.LogicalLocation
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.MultiformatMessageString
import io.github.detekt.sarif4k.PhysicalLocation
import io.github.detekt.sarif4k.Region
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.Run
import io.github.detekt.sarif4k.SarifSchema210
import io.github.detekt.sarif4k.ThreadFlow
import io.github.detekt.sarif4k.ThreadFlowLocation
import io.github.detekt.sarif4k.Tool
import io.github.detekt.sarif4k.ToolComponent
import io.github.detekt.sarif4k.Version
import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.engine.VulnerabilityInstance
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst

private const val SARIF_SCHEMA =
    "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json"
private const val JACODB_INFORMATION_URI =
    "https://github.com/UnitTestBot/jacodb/blob/develop/jacodb-analysis/README.md"
private const val DEFAULT_PATH_COUNT = 3

fun sarifReportFromVulnerabilities(
    vulnerabilities: List<VulnerabilityInstance>,
    maxPathsCount: Int = DEFAULT_PATH_COUNT,
    sourceFileResolver: SourceFileResolver = SourceFileResolver { null },
): SarifSchema210 {
    return SarifSchema210(
        schema = SARIF_SCHEMA,
        version = Version.The210,
        runs = listOf(
            Run(
                tool = Tool(
                    driver = ToolComponent(
                        name = "JaCo-IFDS",
                        organization = "UnitTestBot",
                        version = "1.2.0",
                        informationURI = JACODB_INFORMATION_URI,
                    )
                ),
                results = vulnerabilities.map { instance ->
                    Result(
                        ruleID = instance.vulnerabilityDescription.ruleId,
                        message = Message(
                            text = instance.vulnerabilityDescription.message
                        ),
                        level = when (instance.vulnerabilityDescription.level) {
                            SarifSeverityLevel.ERROR -> Level.Error
                            SarifSeverityLevel.NOTE -> Level.Note
                            SarifSeverityLevel.WARNING -> Level.Warning
                            // else -> Level.None
                        },
                        locations = listOf(instToSarifLocation(instance.traceGraph.sink.statement, sourceFileResolver)),
                        codeFlows = instance.traceGraph
                            .getAllTraces()
                            .take(maxPathsCount)
                            .map { traceToSarifCodeFlow(it, sourceFileResolver) }
                            .toList(),
                    )
                }
            )
        )
    )
}

private val JcMethod.fullyQualifiedName: String
    get() = "${enclosingClass.name}#${name}"

private fun instToSarifLocation(inst: JcInst, sourceFileResolver: SourceFileResolver): Location {
    val instLocation = inst.location.method.declaration.location
    val sourceLocation = sourceFileResolver.resolveSourcePath(instLocation)
        ?: resolveRelativeSourcePath(instLocation) // fallback to relative path
    return Location(
        physicalLocation = PhysicalLocation(
            artifactLocation = ArtifactLocation(
                uri = sourceLocation
            ),
            region = Region(
                startLine = inst.location.lineNumber.toLong()
            )
        ),
        logicalLocations = listOf(
            LogicalLocation(
                fullyQualifiedName = inst.location.method.fullyQualifiedName
            )
        )
    )
}

private fun traceToSarifCodeFlow(trace: List<IfdsVertex>, sourceFileResolver: SourceFileResolver): CodeFlow {
    return CodeFlow(
        threadFlows = listOf(ThreadFlow(
            locations = trace.map {
                ThreadFlowLocation(
                    location = instToSarifLocation(it.statement, sourceFileResolver),
                    state = mapOf(
                        "domainFact" to MultiformatMessageString(
                            text = it.domainFact.toString()
                        )
                    )
                )
            }
        ))
    )
}
