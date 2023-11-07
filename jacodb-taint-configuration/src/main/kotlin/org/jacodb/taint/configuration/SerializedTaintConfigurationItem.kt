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

package org.jacodb.taint.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
sealed interface SerializedTaintConfigurationItem {
    val methodInfo: FunctionMatcher

    fun updateMethodInfo(updatedMethodInfo: FunctionMatcher): SerializedTaintConfigurationItem =
        when (this) {
            is SerializedTaintCleaner -> copy(methodInfo = updatedMethodInfo)
            is SerializedTaintEntryPointSource -> copy(methodInfo = updatedMethodInfo)
            is SerializedTaintMethodSink -> copy(methodInfo = updatedMethodInfo)
            is SerializedTaintMethodSource -> copy(methodInfo = updatedMethodInfo)
            is SerializedTaintPassThrough -> copy(methodInfo = updatedMethodInfo)
        }
}

@Serializable
@SerialName("EntryPointSource")
data class SerializedTaintEntryPointSource(
    override val methodInfo: FunctionMatcher,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : SerializedTaintConfigurationItem

@Serializable
@SerialName("MethodSource")
data class SerializedTaintMethodSource(
    override val methodInfo: FunctionMatcher,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : SerializedTaintConfigurationItem

@Serializable
@SerialName("MethodSink")
data class SerializedTaintMethodSink(
    val ruleNote: String,
    val cwe: List<Int>,
    override val methodInfo: FunctionMatcher,
    val condition: Condition
) : SerializedTaintConfigurationItem

@Serializable
@SerialName("PassThrough")
data class SerializedTaintPassThrough(
    override val methodInfo: FunctionMatcher,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : SerializedTaintConfigurationItem

@Serializable
@SerialName("Cleaner")
data class SerializedTaintCleaner(
    override val methodInfo: FunctionMatcher,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : SerializedTaintConfigurationItem
