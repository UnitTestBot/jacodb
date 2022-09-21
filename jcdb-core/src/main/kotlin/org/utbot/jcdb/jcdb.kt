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
    val environment = SQLitePersistenceImpl(featureRegistry,
        location = File(persistentSettings?.location ?: ":memory:"),
        clearOnStart = persistentSettings?.clearOnStart ?: true
    )
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

private fun JCDBPersistentSettings.toEnvironment(featuresRegistry: FeaturesRegistry): JCDBPersistence {
    return location.let {
        SQLitePersistenceImpl(featuresRegistry, File(it), clearOnStart)
    }
}
