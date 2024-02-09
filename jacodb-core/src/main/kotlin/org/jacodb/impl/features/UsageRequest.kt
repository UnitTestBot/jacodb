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

package org.jacodb.impl.features

import kotlinx.serialization.Serializable
import org.jacodb.api.ClassSource

@Serializable
data class UsageFeatureRequest(
    val methodName: String?,
    val description: String?,
    val field: String?,
    val opcodes: Collection<Int>,
    val className: Set<String>,
) : java.io.Serializable

class UsageFeatureResponse(
    val source: ClassSource,
    val offsets: ShortArray,
)
