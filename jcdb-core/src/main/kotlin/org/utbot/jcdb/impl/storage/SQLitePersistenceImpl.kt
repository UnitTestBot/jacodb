package org.utbot.jcdb.impl.storage

import mu.KLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.utbot.jcdb.api.ByteCodeContainer
import org.utbot.jcdb.api.JCDBPersistence
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.FeaturesRegistry
import org.utbot.jcdb.impl.bytecode.JcClassOrInterfaceImpl
import org.utbot.jcdb.impl.fs.ByteCodeConverter
import org.utbot.jcdb.impl.fs.ClassSourceImpl
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import java.io.Closeable
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SQLitePersistenceImpl(
    private val featuresRegistry: FeaturesRegistry,
    private val location: File? = null,
    private val clearOnStart: Boolean
) : JCDBPersistence, Closeable, ByteCodeConverter {

    companion object : KLogging()

    private val lock = ReentrantLock()

    private val dataSource = SQLiteDataSource(SQLiteConfig().also {
        it.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
        it.setPageSize(32_768)
        it.setCacheSize(-8_000)
    }).also {
        it.url = "jdbc:sqlite:$location"
    }

    internal val db: Database = Database.connect(dataSource)
    private val persistenceService = PersistenceService(this)

    init {
        write {
            if (clearOnStart) {
                SchemaUtils.drop(
                    Classpaths, ClasspathLocations, BytecodeLocations,
                    Classes, Symbols, ClassInterfaces, ClassInnerClasses, OuterClasses,
                    Methods, MethodParameters,
                    Fields,
                    Annotations, AnnotationValues
                )
            }
            SchemaUtils.create(
                Classpaths, ClasspathLocations,
                BytecodeLocations,
                Classes, Symbols, ClassInterfaces, ClassInnerClasses, OuterClasses,
                Methods, MethodParameters,
                Fields,
                Annotations, AnnotationValues,
            )
        }
    }

    override fun setup() {
        write {
            featuresRegistry.jcdbFeatures.forEach {
                it.persistence?.beforeIndexing(clearOnStart)
            }
        }
        persistenceService.setup()
    }

    override val locations: List<JcByteCodeLocation>
        get() {
            return transaction(db) {
                BytecodeLocationEntity.all().toList().mapNotNull {
                    try {
                        File(it.path).asByteCodeLocation(isRuntime = it.runtime)
                    } catch (e: Exception) {
                        null
                    }
                }.toList()
            }
        }

    override fun <T> write(newTx: Boolean, action: () -> T): T {
        return lock.withLock {
            if (newTx) {
                transaction(db) {
                    action()
                }
            } else {
                action()
            }
        }
    }

    override fun <T> read(newTx: Boolean, action: () -> T): T {
        return if (newTx) {
            transaction(db) {
                action()
            }
        } else {
            action()
        }
    }

    override fun findByName(
        cp: JcClasspath,
        locations: List<RegisteredLocation>,
        fullName: String
    ): JcClassOrInterface? {
        val ids = locations.map { it.id }
        return transaction(db) {
            val symbolId = SymbolEntity.find(Symbols.name eq fullName)
                .firstOrNull()?.id?.value ?: return@transaction null
            val found = Classes.slice(Classes.locationId, Classes.bytecode)
                .select(Classes.name eq symbolId and (Classes.locationId inList ids))
                .firstOrNull() ?: return@transaction null
            val locationId = found[Classes.locationId].value
            val byteCode = found[Classes.bytecode]
            JcClassOrInterfaceImpl(
                cp, ClassSourceImpl(
                    location = locations.first { it.id == locationId },
                    className = fullName,
                    byteCode = byteCode
                )
            )

        }
    }

    override fun persist(location: RegisteredLocation, classes: List<ByteCodeContainer>) {
        val allClasses = classes.map {
            it.asmNode.asClassInfo(it.binary)
        }
        write {
            persistenceService.persist(location, allClasses)
        }
    }

    override fun close() {
    }

}

