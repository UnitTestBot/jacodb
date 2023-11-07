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

import org.jacodb.api.JcField
import org.jacodb.api.JcMethod

sealed interface TaintConfigurationItem

data class TaintEntryPointSource(
    val method: JcMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintMethodSource(
    val method: JcMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintFieldSource(
    val field: JcField,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintMethodSink(
    val method: JcMethod,
    val condition: Condition,
) : TaintConfigurationItem

data class TaintFieldSink(
    val field: JcField,
    val condition: Condition,
) : TaintConfigurationItem

data class TaintPassThrough(
    val method: JcMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintCleaner(
    val method: JcMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

val TaintConfigurationItem.condition: Condition
    get() = when (this) {
        is TaintEntryPointSource -> condition
        is TaintMethodSource -> condition
        is TaintFieldSource -> condition
        is TaintMethodSink -> condition
        is TaintFieldSink -> condition
        is TaintPassThrough -> condition
        is TaintCleaner -> condition
    }

val TaintConfigurationItem.actionsAfter: List<Action>
    get() = when (this) {
        is TaintEntryPointSource -> actionsAfter
        is TaintMethodSource -> actionsAfter
        is TaintFieldSource -> actionsAfter
        is TaintMethodSink -> emptyList()
        is TaintFieldSink -> emptyList()
        is TaintPassThrough -> actionsAfter
        is TaintCleaner -> actionsAfter
    }
