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

package org.jacodb.ets.utils

/**
 * Encode a string for use in HTML, replacing special characters with their HTML entities.
 * This is needed for rendering text in Graphviz nodes with HTML labels.
 *
 * For example, `"Hello & <world>"` becomes `"Hello &amp; &lt;world&gt;"`.
 */
internal fun String.htmlEncode(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\\\"", "&quot;")
    .replace("\"", "&quot;")

/**
 * Sanitize an integer ID for use as a Graphviz node ID.
 * Negative IDs are prefixed with "N" to avoid issues with Graphviz.
 *
 * For example, `-123` becomes `"N123"`, while `456` remains `"456"`.
 */
internal fun sanitize(id: Int): String {
    val s = id.toString()
    return if (s.startsWith("-")) "N${s.substring(1)}" else s
}
