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

package org.utbot.jacodb.impl

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.utbot.jacodb.api.Hook
import org.utbot.jacodb.api.JcDatabase
import org.utbot.jacodb.api.JcDatabasePersistence
import org.utbot.jacodb.api.JcFeature
import org.utbot.jacodb.impl.fs.JavaRuntime
import org.utbot.jacodb.impl.storage.PostgresPersistenceImpl
import org.utbot.jacodb.impl.storage.SQLitePersistenceImpl
import java.io.File

/**
 * Settings for database
 */
class JcSettings {

    /** watch file system changes delay */
    var watchFileSystemDelay: Int? = null
        private set

    /** persisted  */
    var persistentType: JcPersistenceType? = null
        private set

    var persistentLocation: String? = null
        private set

    var persistentClearOnStart: Boolean? = null

    /** jar files which should be loaded right after database is created */
    var predefinedDirOrJars: List<File> = persistentListOf()
        private set

    var hooks: MutableList<(JcDatabase) -> Hook> = arrayListOf()
        private set

    /** mandatory setting for java runtime location */
    lateinit var jre: File

    /** features to add */
    var features: List<JcFeature<*, *>> = emptyList()
        private set

    init {
        useProcessJavaRuntime()
    }

    /**
     * builder for persistent settings
     * @param location - file for db location
     * @param clearOnStart -if true old data from this folder will be dropped
     */
    fun persistent(location: String, clearOnStart: Boolean = false, type: JcPersistenceType = JcPersistenceType.SQLITE) = apply {
        persistentLocation = location
        persistentClearOnStart = clearOnStart
        persistentType = type
    }

    fun loadByteCode(files: List<File>) = apply {
        predefinedDirOrJars = (predefinedDirOrJars + files).toPersistentList()
    }

    /**
     * builder for watching file system changes
     * @param delay - delay between syncs
     */
    fun watchFileSystem(delay: Int = 10_000) = apply {
        watchFileSystemDelay = delay
    }

    /** builder for hooks */
    fun withHook(hook: (JcDatabase) -> Hook) = apply {
        hooks += hook
    }

    /**
     * use java from JAVA_HOME env variable
     */
    fun useJavaHomeRuntime() = apply {
        val javaHome = System.getenv("JAVA_HOME") ?: throw IllegalArgumentException("JAVA_HOME is not set")
        jre = javaHome.asValidJRE()
    }

    /**
     * use java from current system process
     */
    fun useProcessJavaRuntime() = apply {
        val javaHome = System.getProperty("java.home") ?: throw IllegalArgumentException("java.home is not set")
        jre = javaHome.asValidJRE()
    }

    /**
     * use java from current system process
     */
    fun useJavaRuntime(runtime: File) = apply {
        jre = runtime.absolutePath.asValidJRE()
    }

    /**
     * install additional indexes
     */
    fun installFeatures(vararg feature: JcFeature<*, *>) = apply {
        features = features + feature.toList()
    }

    private fun String.asValidJRE(): File {
        val file = File(this)
        if (!file.exists()) {
            throw IllegalArgumentException("$this points to folder that do not exists")
        }
        return file
    }
}

enum class JcPersistenceType {
    SQLITE {
        override fun newPersistence(
            runtime: JavaRuntime,
            featuresRegistry: FeaturesRegistry,
            settings: JcSettings
        ): JcDatabasePersistence {
            return SQLitePersistenceImpl(
                javaRuntime = runtime,
                featuresRegistry = featuresRegistry,
                location = settings.persistentLocation,
                clearOnStart = settings.persistentClearOnStart ?: false
            )
        }
    },
    POSTGRES {
        override fun newPersistence(
            runtime: JavaRuntime,
            featuresRegistry: FeaturesRegistry,
            settings: JcSettings
        ): JcDatabasePersistence {
            return PostgresPersistenceImpl(
                javaRuntime = runtime,
                featuresRegistry = featuresRegistry,
                jcdbUrl = settings.persistentLocation,
                clearOnStart = settings.persistentClearOnStart ?: false
            )
        }
    };

    abstract fun newPersistence(
        runtime: JavaRuntime,
        featuresRegistry: FeaturesRegistry,
        settings: JcSettings
    ): JcDatabasePersistence
}