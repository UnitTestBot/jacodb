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

@file:JvmName("Diagnostics")

package org.jacodb.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.jacodb.api.JcClasspath
import org.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.jacodb.impl.storage.jooq.tables.references.SYMBOLS
import org.jooq.impl.DSL

/**
 * finds out duplicates classes
 *
 * @return map with name and count of classes
 */
suspend fun JcClasspath.duplicatedClasses(): Map<String, Int> {
    db.awaitBackgroundJobs()
    return db.persistence.read {
        it.select(SYMBOLS.NAME, DSL.count(SYMBOLS.NAME)).from(CLASSES)
            .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
            .where(CLASSES.LOCATION_ID.`in`(registeredLocations.map { it.id }))
            .groupBy(SYMBOLS.NAME)
            .having(DSL.count(SYMBOLS.NAME).greaterThan(1))
            .fetch()
            .map { (name, count) -> name!! to count!! }
            .toMap()
    }

}

fun JcClasspath.asyncDuplicatedClasses() = GlobalScope.future { duplicatedClasses() }
