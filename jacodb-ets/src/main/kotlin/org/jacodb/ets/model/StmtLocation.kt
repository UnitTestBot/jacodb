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

import org.jacodb.api.common.cfg.CommonInstLocation

/**
 * Origin of an EtsIR statement in the source program.
 *
 * Offsets are UTF-16 offsets, matching the TypeScript compiler API. Lines and
 * columns are zero-based. Several normalized EtsIR statements may share the
 * same origin when one source expression is lowered into three-address code.
 */
data class EtsSourceSpan(
    val fileName: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val nodeKind: String,
)

data class EtsStmtLocation(
    override val method: EtsMethod,
    var index: Int,
) : CommonInstLocation {
    /**
     * Source origin of the statement, when the frontend provided one.
     *
     * Deliberately kept OUT of the primary constructor: it must not participate in
     * `equals`/`hashCode` of the location (and hence of every `EtsStmt`), which would
     * make statement equality depend on the frontend in use — ArkAnalyzer emits no
     * origins at all, the TS frontend does.
     *
     * NB: as a consequence, the generated [copy] and `toString` do NOT carry `origin`;
     * `copy()` always returns a location with `origin == null`. Use the three-argument
     * secondary constructor (or [stub]) when the origin must be preserved.
     */
    var origin: EtsSourceSpan? = null

    constructor(method: EtsMethod, index: Int, origin: EtsSourceSpan?) : this(method, index) {
        this.origin = origin
    }

    companion object {
        fun stub(method: EtsMethod, origin: EtsSourceSpan? = null): EtsStmtLocation {
            return EtsStmtLocation(method, -1, origin)
        }
    }
}
