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

package org.jacodb.impl.storage.ers

import org.jacodb.api.jvm.ClassSource
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.storage.ers.Entity
import org.jacodb.impl.fs.PersistenceClassSource

fun Sequence<Entity>.toClassSourceSequence(db: JcDatabase): Sequence<ClassSource> {
    val persistence = db.persistence
    return map { clazz ->
        val classId: Long = clazz.id.instanceId
        PersistenceClassSource(
            db = db,
            className = persistence.findSymbolName(clazz.getCompressed<Long>("nameId")!!),
            classId = classId,
            locationId = clazz.getCompressed<Long>("locationId")!!,
            cachedByteCode = persistence.findBytecode(classId)
        )
    }
}