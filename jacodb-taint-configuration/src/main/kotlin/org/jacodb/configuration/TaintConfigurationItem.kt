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

package org.jacodb.configuration

import org.jacodb.api.JcField
import org.jacodb.api.JcMethod

sealed interface TaintConfigurationItem

data class TaintEntryPointSource(
    val methodInfo: JcMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintMethodSource(
    val methodInfo: JcMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintFieldSource(
    val fieldInfo: JcField,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintMethodSink(
    val condition: Condition,
    val methodInfo: JcMethod,
) : TaintConfigurationItem

data class TaintFieldSink(
    val condition: Condition,
    val fieldInfo: JcField,
) : TaintConfigurationItem

data class TaintPassThrough(
    val methodInfo: JcMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintCleaner(
    val methodInfo: JcMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

