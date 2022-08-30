package org.utbot.jcdb.impl.storage

import mu.KLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteConnection
import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.LocationScope
import org.utbot.jcdb.impl.JCDBImpl
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.storage.scheme.LocationStore
import java.io.Closeable
import java.io.File

class PersistentEnvironment(id: String, location: File? = null, clearOnStart: Boolean) : Closeable {

    companion object : KLogging()

//    private val home = (location?.toPath() ?: Paths.get(System.getProperty("user.home"), ".jdbc")).let {
//        val file = it.toFile()
//        if (!file.exists() && !file.mkdirs()) {
//            throw DataStorageException("can't create storage in ${file.absolutePath}")
//        }
//        file
//    }

//    private val env = Environments.newInstance(home, EnvironmentConfig().setEnvCloseForcedly(true))
//    private val persistentEntityStore = PersistentEntityStores.newInstance(env, id)

    init {
        Database.connect("jdbc:sqlite:jcdb", setupConnection = {
            it as SQLiteConnection
        })
        transaction {
            if (clearOnStart) {
                SchemaUtils.drop(
                    BytecodeLocations,
                    Packages,
                    Classes, ClassNames, ClassInterfaces, ClassInnerClasses, OuterClasses,
                    Methods, MethodParameters,
                    Fields
                )
            }
            SchemaUtils.create(
                BytecodeLocations,
                Packages,
                Classes, ClassNames, ClassInterfaces, ClassInnerClasses, OuterClasses,
                Methods, MethodParameters,
                Fields
            )
        }
    }

    val locationStore = LocationStore(this)
//    val globalIds = GlobalIdStore(persistentEntityStore.environment)

    val allByteCodeLocations: List<Pair<BytecodeLocationEntity, ByteCodeLocation>>
        get() {
            return transaction {
                locationStore.all.mapNotNull {
                    try {
                        it to File(it.path).asByteCodeLocation(isRuntime = it.runtime)
                    } catch (e: Exception) {
                        null
                    }
                }.toList()
            }
        }

    fun save(db: JCDBImpl, clearOnStart: Boolean) {
        transaction {
            db.locationsRegistry.locations.forEach { location ->
                BytecodeLocationEntity.new {
                    path = location.path
                    runtime = location.scope == LocationScope.RUNTIME
                }
            }
        }
    }

    override fun close() {
    }
}

