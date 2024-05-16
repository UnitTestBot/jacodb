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

package parser

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.jacodb.api.common.CommonMethod
import org.jacodb.panda.dynamic.parser.IRParser
import org.jacodb.panda.dynamic.parser.TSParser
import org.jacodb.panda.dynamic.parser.dumpDot
import org.jacodb.taint.configuration.NameExactMatcher
import org.jacodb.taint.configuration.NamePatternMatcher
import org.jacodb.taint.configuration.SerializedTaintCleaner
import org.jacodb.taint.configuration.SerializedTaintConfigurationItem
import org.jacodb.taint.configuration.SerializedTaintEntryPointSource
import org.jacodb.taint.configuration.SerializedTaintMethodSink
import org.jacodb.taint.configuration.SerializedTaintMethodSource
import org.jacodb.taint.configuration.SerializedTaintPassThrough
import org.jacodb.taint.configuration.TaintCleaner
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintEntryPointSource
import org.jacodb.taint.configuration.TaintMethodSink
import org.jacodb.taint.configuration.TaintMethodSource
import org.jacodb.taint.configuration.TaintPassThrough
import org.jacodb.taint.configuration.actionModule
import org.jacodb.taint.configuration.conditionModule
import java.io.File

fun loadIr(filePath: String): IRParser {
    val sampleFilePath = object {}::class.java.getResource(filePath)?.path
        ?: error("Resource not found: $filePath")
    return IRParser(sampleFilePath)
}

fun loadIrWithTs(filePath: String, tsPath: String): IRParser {
    val sampleFilePath = object {}::class.java.getResource(filePath)?.path
        ?: error("Resource not found: $filePath")
    val sampleTSPath = object {}::class.java.getResource(tsPath)?.toURI()
        ?: error("Resource not found: $tsPath")
    val tsParser = TSParser(sampleTSPath)
    val tsFunctions = tsParser.collectFunctions()
    return IRParser(sampleFilePath, tsFunctions)
}

fun loadRules(configFileName: String): List<SerializedTaintConfigurationItem> {
    val configResource = object {}::class.java.getResourceAsStream("/$configFileName")
        ?: error("Could not load config from '$configFileName'")
    val configJson = configResource.bufferedReader().readText()
    val rules: List<SerializedTaintConfigurationItem> = Json {
        classDiscriminator = "_"
        serializersModule = SerializersModule {
            include(conditionModule)
            include(actionModule)
        }
    }.decodeFromString(configJson)
    // println("Loaded ${rules.size} rules from '$configFileName'")
    // for (rule in rules) {
    //     println(rule)
    // }
    return rules
}

fun getConfigForMethod(
    method: CommonMethod<*, *>,
    rules: List<SerializedTaintConfigurationItem>,
): List<TaintConfigurationItem>? {
    val res = buildList {
        for (item in rules) {
            val matcher = item.methodInfo.functionName
            if (matcher is NameExactMatcher) {
                if (method.name == matcher.name) add(item.toItem(method))
            } else if (matcher is NamePatternMatcher) {
                if (method.name.matches(matcher.pattern.toRegex())) add(item.toItem(method))
            }
        }
    }
    return res.ifEmpty { null }
}

fun SerializedTaintConfigurationItem.toItem(method: CommonMethod<*, *>): TaintConfigurationItem {
    return when (this) {
        is SerializedTaintEntryPointSource -> TaintEntryPointSource(
            method = method,
            condition = condition,
            actionsAfter = actionsAfter
        )

        is SerializedTaintMethodSource -> TaintMethodSource(
            method = method,
            condition = condition,
            actionsAfter = actionsAfter
        )

        is SerializedTaintMethodSink -> TaintMethodSink(
            method = method,
            ruleNote = ruleNote,
            cwe = cwe,
            condition = condition
        )

        is SerializedTaintPassThrough -> TaintPassThrough(
            method = method,
            condition = condition,
            actionsAfter = actionsAfter
        )

        is SerializedTaintCleaner -> TaintCleaner(
            method = method,
            condition = condition,
            actionsAfter = actionsAfter
        )
    }
}

object DumpIrToDot {
    @JvmStatic
    fun main(args: Array<String>) {
        val name = "PhiTest"
        val parser = loadIrWithTs("/samples/$name.json", "/samples/$name.ts")
        val program = parser.getProgram()
        val project = parser.getProject()
        println(program)
        println(project)

        val path = "dump"
        val dotFile = File("$path.dot")
        program.dumpDot(dotFile)
        println("Generated DOT file: ${dotFile.absolutePath}")
        for (format in listOf("pdf")) {
            val formatFile = File("$path.$format")
            val p = Runtime.getRuntime().exec("dot -T$format $dotFile -o $formatFile")
            p.waitFor()
            print(p.inputStream.bufferedReader().readText())
            print(p.errorStream.bufferedReader().readText())
            println("Generated ${format.uppercase()} file: ${formatFile.absolutePath}")
        }
    }
}
