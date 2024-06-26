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

package ark.utils

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.jacodb.api.common.CommonMethod
import org.jacodb.panda.taint.CaseTaintConfig
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

private val json = Json {
    classDiscriminator = "_"
    serializersModule = SerializersModule {
        include(conditionModule)
        include(actionModule)
    }
}

fun loadRules(configFileName: String): List<SerializedTaintConfigurationItem> {
    val configResource = object {}::class.java.getResourceAsStream("/$configFileName")
        ?: error("Could not load config from '$configFileName'")
    val configJson = configResource.bufferedReader().readText()
    val rules: List<SerializedTaintConfigurationItem> = json.decodeFromString(configJson)
    // println("Loaded ${rules.size} rules from '$configFileName'")
    // for (rule in rules) {
    //     println(rule)
    // }
    return rules
}

fun getConfigForMethod(
    method: CommonMethod,
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

fun SerializedTaintConfigurationItem.toItem(method: CommonMethod): TaintConfigurationItem {
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

fun loadCaseTaintConfig(filename: String): CaseTaintConfig {
    val configResource = object {}::class.java.getResourceAsStream("/arkir/samples/taintConfigs/$filename")
        ?: error("Could not load config from '$filename'")
    val configJson = configResource.bufferedReader().readText()
    val config: CaseTaintConfig = Json.decodeFromString(configJson)
    return config
}
