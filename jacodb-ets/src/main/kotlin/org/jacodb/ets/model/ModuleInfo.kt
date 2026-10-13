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

/** Import metadata as emitted by the frontend; this does not resolve the imported binding. */
data class EtsImportInfo(
    val importClauseName: String,
    val importType: String,
    val importFrom: String? = null,
    val modifiers: EtsModifiers = EtsModifiers.EMPTY,
    val nameBeforeAs: String? = null,
)

/** Export metadata as emitted by the frontend; unknown clause kinds are retained verbatim. */
data class EtsExportInfo(
    val exportClauseName: String,
    val exportClauseType: Int,
    val exportFrom: String? = null,
    val modifiers: EtsModifiers = EtsModifiers.EMPTY,
    val nameBeforeAs: String? = null,
)
