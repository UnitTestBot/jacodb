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

package org.jacodb.ets.model

/**
 * Represents export information for TypeScript exports.
 */
data class EtsExportInfo(
    val name: String,
    val type: EtsExportType,
    val from: String? = null,
    val originalName: String? = null,
    override val modifiers: EtsModifiers = EtsModifiers.EMPTY,
) : Base {

    // Export statements do not have decorators in TypeScript.
    override val decorators: List<EtsDecorator> = emptyList()

    /**
     * Whether this export is a default export.
     */
    val isDefaultExport: Boolean
        get() {
            if (from != null) return originalName == "default"
            return name == "default" || super.isDefault
        }

    override val isDefault: Boolean
        get() = isDefaultExport

    override fun toString(): String {
        val alias = if (originalName != null) {
            " as $name"
        } else ""

        val from = from?.let { " from '$it'" } ?: ""
        val defaultPrefix = if (isDefaultExport) "default " else ""
        val originName = originalName ?: name

        return "export $defaultPrefix$originName$alias$from"
    }
}

enum class EtsExportType(val value: Int) {
    NAME_SPACE(0),
    CLASS(1),
    METHOD(2),
    LOCAL(3),
    TYPE(4),
    UNKNOWN(9);

    companion object {
        fun from(value: Int): EtsExportType {
            return entries.find { it.value == value } ?: UNKNOWN
        }
    }
}
