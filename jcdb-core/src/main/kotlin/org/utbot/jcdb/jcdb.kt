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

@file:JvmName("JacoDB")
package org.utbot.jcdb

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.impl.FeaturesRegistry
import org.utbot.jcdb.impl.JCDBImpl
import org.utbot.jcdb.impl.fs.JavaRuntime
import org.utbot.jcdb.impl.storage.SQLitePersistenceImpl

suspend fun jacodb(builder: JcSettings.() -> Unit): JCDB {
    return jacodb(JcSettings().also(builder))
}

suspend fun jacodb(settings: JcSettings): JCDB {
    val featureRegistry = FeaturesRegistry(settings.features)
    val javaRuntime = JavaRuntime(settings.jre)
    val environment = SQLitePersistenceImpl(
        javaRuntime,
        featureRegistry,
        location = settings.persistentLocation,
        clearOnStart = settings.persistentClearOnStart ?: true
    )
    return JCDBImpl(
        javaRuntime = javaRuntime,
        persistence = environment,
        featureRegistry = featureRegistry,
        settings = settings
    ).also {
        it.restore()
        it.afterStart()
    }
}

/** bridge for Java */
fun async(settings: JcSettings) = GlobalScope.future { jacodb(settings) }