package org.utbot.jcdb.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.utbot.jcdb.JCDBSettings
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JCDBPersistence
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.fs.JavaRuntime
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.fs.filterExisted
import org.utbot.jcdb.impl.fs.load
import org.utbot.jcdb.impl.storage.PersistentLocationRegistry
import org.utbot.jcdb.impl.storage.SQLitePersistenceImpl
import org.utbot.jcdb.impl.vfs.GlobalClassesVfs
import org.utbot.jcdb.impl.vfs.RemoveLocationsVisitor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class JCDBImpl(
    override val persistence: JCDBPersistence,
    val featureRegistry: FeaturesRegistry,
    private val settings: JCDBSettings
) : JCDB {

    private val classesVfs = GlobalClassesVfs()
    internal val javaRuntime = JavaRuntime(settings.jre)
    private val hooks = settings.hooks.map { it(this) }

    internal val locationsRegistry: LocationsRegistry
    private val backgroundJobs = ConcurrentHashMap<Int, Job>()

    private val isClosed = AtomicBoolean()
    private val jobId = AtomicInteger()

    init {
        featureRegistry.bind(this)
        // todo rewrite in more elegant way
        locationsRegistry =  PersistentLocationRegistry(persistence as SQLitePersistenceImpl, featureRegistry)
    }

    private lateinit var runtimeLocations: List<RegisteredLocation>

    override val locations: List<JcByteCodeLocation>
        get() = locationsRegistry.locations.toList()

    suspend fun loadJavaLibraries() {
        assertNotClosed()
        runtimeLocations = javaRuntime.allLocations.loadAll()
    }

    override suspend fun classpath(dirOrJars: List<File>): JcClasspath {
        assertNotClosed()
        val existedLocations = dirOrJars.filterExisted().map { it.asByteCodeLocation() }.loadAll()
        val classpathSetLocations = existedLocations.toList() + runtimeLocations
        return JcClasspathImpl(
            locationsRegistry.snapshot(classpathSetLocations),
            featureRegistry,
            this,
            classesVfs
        )
    }

    fun classpath(locations: List<RegisteredLocation>): JcClasspath {
        assertNotClosed()
        val classpathSetLocations = locations.toSet() + runtimeLocations
        return JcClasspathImpl(
            locationsRegistry.snapshot(classpathSetLocations.toList()),
            featureRegistry,
            this,
            classesVfs
        )
    }

    override suspend fun load(dirOrJar: File) = apply {
        assertNotClosed()
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        assertNotClosed()
        dirOrJars.filterExisted().map { it.asByteCodeLocation() }.loadAll()
    }

    override suspend fun loadLocations(locations: List<JcByteCodeLocation>) = apply {
        assertNotClosed()
        locations.loadAll()
    }

    private suspend fun List<JcByteCodeLocation>.loadAll(): List<RegisteredLocation> {
        val actions = ConcurrentLinkedQueue<RegisteredLocation>()

        val registeredLocations = locationsRegistry.addLocations(this)

        val libraryTrees = withContext(Dispatchers.IO) {
            registeredLocations.map { location ->
                async {
                    // here something may go wrong
                    val libraryTree = location.load()
                    actions.add(location)
                    libraryTree
                }
            }
        }.awaitAll()
        persistence.write {
            persistence.save(this@JCDBImpl)
        }

        val locationClasses = libraryTrees.associate {
            it.location to it.pushInto(classesVfs).values
        }
        val backgroundJobId = jobId.incrementAndGet()
        backgroundJobs[backgroundJobId] = BackgroundScope.launch {
            val parentScope = this
            actions.map { location ->
                async {
                    if (parentScope.isActive) {
                        val addedClasses = locationClasses[location]
                        if (addedClasses != null) {
                            if (parentScope.isActive) {
                                persistence.persist(location, addedClasses.toList())
                                featureRegistry.index(location, addedClasses)
                            }
                        }
                    }
                }
            }.joinAll()

            backgroundJobs.remove(backgroundJobId)
        }
        return registeredLocations
    }

    override suspend fun refresh() {
        awaitBackgroundJobs()
        locationsRegistry.refresh {
            listOf(it.jcLocation).loadAll()
        }
        val outdatedLocations = locationsRegistry.cleanup()
        classesVfs.visit(RemoveLocationsVisitor(outdatedLocations))
    }

    override suspend fun rebuildFeatures() {
        awaitBackgroundJobs()
        // todo implement me
    }

    override fun watchFileSystemChanges(): JCDB {
        val delay = settings.watchFileSystemChanges?.delay
        if (delay != null) { // just paranoid check
            BackgroundScope.launch {
                while (true) {
                    delay(delay)
                    refresh()
                }
            }
        }
        return this
    }

    override suspend fun awaitBackgroundJobs() {
        backgroundJobs.values.toList().joinAll()
    }

    fun afterStart() {
        hooks.forEach { it.afterStart() }
    }

    override fun close() {
        isClosed.set(true)
        locationsRegistry.close()
        backgroundJobs.values.forEach {
            it.cancel()
        }
        backgroundJobs.clear()
        persistence.close()
        hooks.forEach { it.afterStop() }
    }

    private fun assertNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("Database is already closed")
        }
    }

}