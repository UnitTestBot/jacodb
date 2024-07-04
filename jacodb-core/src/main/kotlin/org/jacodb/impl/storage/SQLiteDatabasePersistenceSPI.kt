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

package org.jacodb.impl.storage

import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.JcDatabasePersistence
import org.jacodb.impl.JcDatabaseImpl
import org.jacodb.impl.JcDatabasePersistenceSPI
import org.jacodb.impl.JcSettings
import org.jacodb.impl.LocationsRegistry
import org.jacodb.impl.fs.JavaRuntime

const val SQLITE_DATABASE_PERSISTENCE_SPI = "org.jacodb.impl.storage.SQLiteDatabasePersistenceSPI"

class SQLiteDatabasePersistenceSPI : JcDatabasePersistenceSPI {

    override val id = SQLITE_DATABASE_PERSISTENCE_SPI

    override fun newPersistence(runtime: JavaRuntime, settings: JcSettings): JcDatabasePersistence {
        return SQLitePersistenceImpl(
            javaRuntime = runtime,
            location = settings.persistenceLocation,
            clearOnStart = settings.persistenceClearOnStart ?: false
        )
    }

    override fun newLocationsRegistry(jcdb: JcDatabase): LocationsRegistry {
        return PersistentLocationsRegistry(jcdb as JcDatabaseImpl)
    }
}