package org.utbot.jcdb

import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.impl.FeaturesRegistry
import org.utbot.jcdb.impl.JCDBImpl
import org.utbot.jcdb.impl.storage.SQLitePersistenceImpl
import java.io.File

suspend fun jcdb(builder: JCDBSettings.() -> Unit): JCDB {
    val settings = JCDBSettings().also(builder)
    val persistentSettings = settings.persistentSettings
    val featureRegistry = FeaturesRegistry(settings.fullFeatures)
    val environment = SQLitePersistenceImpl(
        featureRegistry,
        location = File(persistentSettings?.location ?: ":memory:"),
        clearOnStart = persistentSettings?.clearOnStart ?: true
    )
    return JCDBImpl(
        persistence = environment,
        featureRegistry = featureRegistry,
        settings = settings
    ).also {
        it.restore()
        it.afterStart()
    }
}