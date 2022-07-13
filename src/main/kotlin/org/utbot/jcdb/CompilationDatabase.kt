package org.utbot.jcdb

import org.utbot.jcdb.api.CompilationDatabase
import org.utbot.jcdb.impl.CompilationDatabaseImpl
import org.utbot.jcdb.impl.fs.JavaRuntime
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.storage.PersistentEnvironment
import java.io.File

suspend fun compilationDatabase(builder: CompilationDatabaseSettings.() -> Unit): CompilationDatabase {
    val settings = CompilationDatabaseSettings().also(builder)
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
            val database = CompilationDatabaseImpl(
                persistentEnvironment = environment,
                settings = settings
            )
            database.restoreDataFrom(restoredLocations.toMap())
            database.loadLocations(notLoaded.toList())
            return database
        }
    }
    val database = CompilationDatabaseImpl(null, settings)
    database.loadJavaLibraries()
    if (settings.predefinedDirOrJars.isNotEmpty()) {
        database.load(settings.predefinedDirOrJars)
    }
    if (settings.watchFileSystemChanges != null) {
        database.watchFileSystemChanges()
    }
    return database
}

private fun CompilationDatabasePersistentSettings.toEnvironment(): PersistentEnvironment? {
    return this.location?.let {
        PersistentEnvironment(key, File(it))
    }
}
