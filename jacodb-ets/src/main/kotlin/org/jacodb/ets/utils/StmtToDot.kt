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

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsThrowStmt

internal fun EtsStmt.toDotLabel(): String {
    val label = when (this) {
        is EtsNopStmt -> "nop"
        is EtsAssignStmt -> "$lhv := $rhv"
        is EtsReturnStmt -> returnValue?.let { "return $it" } ?: "return"
        is EtsThrowStmt -> "throw $exception"
        is EtsIfStmt -> "if ($condition)"
        is EtsCallStmt -> "call $expr"
        is EtsRawStmt -> "raw $kind"
        else -> error("Unsupported statement: $this")
    }
    return label.replace("\"", "\\\"")
}
