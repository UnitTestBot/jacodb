package org.utbot.jcdb

import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.impl.JCDBImpl
import org.utbot.jcdb.impl.fs.JavaRuntime
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.storage.PersistentEnvironment
import java.io.File

suspend fun jcdb(builder: JCDBSettings.() -> Unit): JCDB {
    val settings = JCDBSettings().also(builder)
    val persistentSettings = settings.persistentSettings
    if (persistentSettings != null) {
        val environment = persistentSettings.toEnvironment()
        if (environment != null) {
            val restoredLocations = environment.allByteCodeLocations
            val byteCodeLocations = restoredLocations.map { it.second }.toList()
            val notLoaded = (
                    JavaRuntime(settings.jre).allLocations +
                            settings.predefinedDirOrJars
                                .filter { it.exists() }
                                .map { it.asByteCodeLocation(isRuntime = false) }
                    ).toSet() - byteCodeLocations.toSet()
            val database = JCDBImpl(
                persistentEnvironment = environment,
                settings = settings
            )
            database.restoreDataFrom(restoredLocations.toMap())
            database.loadLocations(notLoaded.toList())
            database.afterStart()
            return database
        }
    }
    val database = JCDBImpl(null, settings)
    database.loadJavaLibraries()
    if (settings.predefinedDirOrJars.isNotEmpty()) {
        database.load(settings.predefinedDirOrJars)
    }
    if (settings.watchFileSystemChanges != null) {
        database.watchFileSystemChanges()
    }
    database.afterStart()
    return database
}

private fun JCDBPersistentSettings.toEnvironment(): PersistentEnvironment? {
    return this.location?.let {
        PersistentEnvironment(key, File(it))
    }
}
