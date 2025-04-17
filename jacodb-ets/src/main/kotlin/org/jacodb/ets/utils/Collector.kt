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

@file:Suppress("PropertyName")

package org.jacodb.ets.utils

import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsStmt

class EntityCollector<R : Any, C : MutableCollection<R>>(
    val result: C,
    val block: (EtsEntity) -> R?,
) : AbstractHandler() {
    override fun handle(value: EtsEntity) {
        val item = block(value)
        if (item != null) {
            result += item
        }
    }

    override fun handle(stmt: EtsStmt) {
        // Do nothing.
    }
}

fun <R : Any, C : MutableCollection<R>> EtsEntity.collectEntitiesTo(
    destination: C,
    block: (EtsEntity) -> R?,
): C {
    accept(EntityCollector(destination, block))
    return destination
}

fun <R : Any, C : MutableCollection<R>> EtsStmt.collectEntitiesTo(
    destination: C,
    block: (EtsEntity) -> R?,
): C {
    accept(EntityCollector(destination, block))
    return destination
}
