package org.utbot.jcdb

import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.impl.FeaturesRegistry
import org.utbot.jcdb.impl.JCDBImpl
import org.utbot.jcdb.impl.storage.SQLitePersistenceImpl

suspend fun jcdb(builder: JCDBSettings.() -> Unit): JCDB {
    return jcdb(JCDBSettings().also(builder))
}

suspend fun jcdb(settings: JCDBSettings): JCDB {
    val featureRegistry = FeaturesRegistry(settings.features)
    val environment = SQLitePersistenceImpl(
        featureRegistry,
        location = settings.persistentLocation,
        clearOnStart = settings.persistentClearOnStart ?: true
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