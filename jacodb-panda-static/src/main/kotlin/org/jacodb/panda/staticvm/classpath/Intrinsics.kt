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

package org.jacodb.panda.staticvm.classpath

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

object Intrinsics {
    @Serializable
    data class IntrinsicMethod(
        val intrinsicId: String,
        val className: String?,
        val methodName: String?,
    )

    private val intrinsicsMapping: Map<String, IntrinsicMethod> = run {
        val resource = this::class.java.getResourceAsStream("/intrinsics.json")
            ?: error("Intrinsics mapping not found")

        @OptIn(ExperimentalSerializationApi::class)
        val intrinsics: List<IntrinsicMethod> = Json.decodeFromStream(resource)

        intrinsics.associateBy { it.intrinsicId }
    }

    fun resolve(id: String): Pair<String, String>? = intrinsicsMapping[id]?.let {
        if (it.className != null && it.methodName != null) {
            Pair(it.className, it.methodName)
        } else {
            null
        }
    }
}
