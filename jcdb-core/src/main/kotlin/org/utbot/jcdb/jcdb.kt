package org.utbot.jcdb

import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JCDBPersistence
import org.utbot.jcdb.impl.FeaturesRegistry
import org.utbot.jcdb.impl.JCDBImpl
import org.utbot.jcdb.impl.fs.JavaRuntime
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.storage.SQLitePersistenceImpl
import java.io.File

suspend fun jcdb(builder: JCDBSettings.() -> Unit): JCDB {
    val settings = JCDBSettings().also(builder)
    val persistentSettings = settings.persistentSettings
    val featureRegistry = FeaturesRegistry(settings.fullFeatures)
    if (persistentSettings != null) {
        val environment = persistentSettings.toEnvironment(featureRegistry)
        if (environment != null) {
            val restoredLocations = environment.locations.toSet()
            val notLoaded = (
                    JavaRuntime(settings.jre).allLocations +
                            settings.predefinedDirOrJars
                                .filter { it.exists() }
                                .map { it.asByteCodeLocation(isRuntime = false) }
                    ).toSet() - restoredLocations
            val database = JCDBImpl(
                persistence = environment,
                featureRegistry = featureRegistry,
                settings = settings
            )
            environment.setup()
            database.loadLocations(notLoaded.toList())
            database.afterStart()
            return database
        }
    }
    val database = JCDBImpl(null, featureRegistry, settings)
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

private fun JCDBPersistentSettings.toEnvironment(featuresRegistry: FeaturesRegistry): JCDBPersistence? {
    return location?.let {
        SQLitePersistenceImpl(featuresRegistry, File(it), clearOnStart)
    }
}
