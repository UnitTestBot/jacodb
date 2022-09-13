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
import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.Classpath
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JCDBPersistence
import org.utbot.jcdb.impl.fs.JavaRuntime
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.fs.filterExisted
import org.utbot.jcdb.impl.fs.load
import org.utbot.jcdb.impl.storage.BytecodeLocationEntity.Companion.findOrNew
import org.utbot.jcdb.impl.tree.ClassTree
import org.utbot.jcdb.impl.tree.RemoveLocationsVisitor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class JCDBImpl(
    override val persistence: JCDBPersistence? = null,
    val featureRegistry: FeaturesRegistry,
    private val settings: JCDBSettings
) : JCDB {

    private val classTree = ClassTree()
    internal val javaRuntime = JavaRuntime(settings.jre)
    private val hooks = settings.hooks.map { it(this) }

    internal val locationsRegistry = LocationsRegistry(featureRegistry)
    private val backgroundJobs = ConcurrentHashMap<Int, Job>()

    private val isClosed = AtomicBoolean()
    private val jobId = AtomicInteger()

    init {
        featureRegistry.bind(this)
    }

    override val locations: List<ByteCodeLocation>
        get() = locationsRegistry.locations.toList()

    suspend fun loadJavaLibraries() {
        assertNotClosed()
        javaRuntime.allLocations.loadAll()
    }

    override suspend fun classpathSet(dirOrJars: List<File>): Classpath {
        assertNotClosed()
        val existedLocations = dirOrJars.filterExisted().map { it.asByteCodeLocation() }.also {
            it.loadAll()
        }
        val classpathSetLocations = existedLocations.toList() + javaRuntime.allLocations
        return ClasspathImpl(
            locationsRegistry.snapshot(classpathSetLocations),
            featureRegistry,
            this,
            classTree
        )
    }

    fun classpathSet(locations: List<ByteCodeLocation>): Classpath {
        assertNotClosed()
        val classpathSetLocations = locations.toSet() + javaRuntime.allLocations
        return ClasspathImpl(
            locationsRegistry.snapshot(classpathSetLocations.toList()),
            featureRegistry,
            this,
            classTree
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

    override suspend fun loadLocations(locations: List<ByteCodeLocation>) = apply {
        assertNotClosed()
        locations.loadAll()
    }

    private suspend fun List<ByteCodeLocation>.loadAll() = apply {
        val actions = ConcurrentLinkedQueue<ByteCodeLocation>()

        val libraryTrees = withContext(Dispatchers.IO) {
            map { location ->
                async {
                    val loader = location.loader()
                    // here something may go wrong
                    if (loader != null) {
                        val libraryTree = loader.load()
                        actions.add(location)
                        locationsRegistry.addLocation(location)
                        persistence?.write {
                            location.findOrNew()
                        }
                        libraryTree
                    } else {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
        persistence?.write {
            persistence.save(this@JCDBImpl)
        }

        val locationClasses = libraryTrees.associate {
            it.location to it.pushInto(classTree).values
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
                                persistence?.write {
                                    persistence.persist(location, addedClasses.toList())
                                }
                                featureRegistry.index(location, addedClasses)
                            }
                        }
                    }
                }
            }.joinAll()

            backgroundJobs.remove(backgroundJobId)
        }
    }

    override suspend fun refresh() {
        awaitBackgroundJobs()
        locationsRegistry.refresh {
            listOf(it).loadAll()
        }
        val outdatedLocations = locationsRegistry.cleanup()
        classTree.visit(RemoveLocationsVisitor(outdatedLocations))
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
        persistence?.close()
        hooks.forEach { it.afterStop() }
    }

    private fun assertNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("Database is already closed")
        }
    }

}