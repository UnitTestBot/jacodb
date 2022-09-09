package org.utbot.jcdb

import org.utbot.jcdb.api.Feature
import org.utbot.jcdb.api.Hook
import org.utbot.jcdb.api.JCDB
import java.io.File

/**
 * Settings for database
 */
class JCDBSettings {
    /** watch file system changes setting */
    var watchFileSystemChanges: JCDBWatchFileSystemSettings? = null

    /** persisted  */
    var persistentSettings: JCDBPersistentSettings? = null

    /** jar files which should be loaded right after database is created */
    var predefinedDirOrJars: List<File> = emptyList()

    var hooks: MutableList<(JCDB) -> Hook> = arrayListOf()

    /** mandatory setting for java location */
    lateinit var jre: File

    /** feature to add */
    var features: List<Feature<*, *>> = emptyList()

    val fullFeatures get() = features

    /** builder for persistent settings */
    fun persistent(settings: (JCDBPersistentSettings.() -> Unit) = {}) {
        persistentSettings = JCDBPersistentSettings().also(settings)
    }

    /** builder for watching file system changes */
    fun watchFileSystem(settings: (JCDBWatchFileSystemSettings.() -> Unit) = {}) {
        watchFileSystemChanges = JCDBWatchFileSystemSettings().also(settings)
    }

    /** builder for hooks */
    fun withHook(hook: (JCDB) -> Hook) {
        hooks += hook
    }

    /**
     * use java from JAVA_HOME env variable
     */
    fun useJavaHomeJavaRuntime() {
        val javaHome = System.getenv("JAVA_HOME") ?: throw IllegalArgumentException("JAVA_HOME is not set")
        jre = javaHome.asValidJRE()
    }

    /**
     * use java from current system process
     */
    fun useProcessJavaRuntime() {
        val javaHome = System.getProperty("java.home") ?: throw IllegalArgumentException("java.home is not set")
        jre = javaHome.asValidJRE()
    }

    /**
     * install additional indexes
     */
    fun installFeatures(vararg feature: Feature<*, *>) {
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


class JCDBPersistentSettings {
    /** location folder for persisting data */
    var location: String? = null

    /** key for data */
    var key: String = "jcdb"

    /** if true old data from this folder will be dropped */
    var clearOnStart: Boolean = false
}

class JCDBWatchFileSystemSettings {
    /** delay between looking up for new changes */
    var delay: Long? = 10_000 // 10 seconds
}