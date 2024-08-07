/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jacodb.impl

import kotlinx.coroutines.*
import org.jacodb.api.jvm.*
import org.jacodb.impl.features.classpaths.ClasspathCache
import org.jacodb.impl.features.classpaths.KotlinMetadata
import org.jacodb.impl.features.classpaths.MethodInstructionsFeature
import org.jacodb.impl.fs.JavaRuntime
import org.jacodb.impl.fs.asByteCodeLocation
import org.jacodb.impl.fs.filterExisting
import org.jacodb.impl.fs.lazySources
import org.jacodb.impl.fs.sources
import org.jacodb.impl.storage.SQLITE_DATABASE_PERSISTENCE_SPI
import org.jacodb.impl.vfs.GlobalClassesVfs
import org.jacodb.impl.vfs.RemoveLocationsVisitor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class JcDatabaseImpl(
    internal val javaRuntime: JavaRuntime,
    private val settings: JcSettings
) : JcDatabase {

    override val persistence: JcDatabasePersistence
    internal val featuresRegistry: FeaturesRegistry
    internal val locationsRegistry: LocationsRegistry

    private val classesVfs = GlobalClassesVfs()
    private val hooks = settings.hooks.map { it(this) }

    private val backgroundJobs = ConcurrentHashMap<Int, Job>()

    private val isClosed = AtomicBoolean()
    private val jobId = AtomicInteger()

    private val backgroundScope = BackgroundScope()

    init {
        val persistenceId = (settings.persistenceId ?: SQLITE_DATABASE_PERSISTENCE_SPI)
        val persistenceSPI = JcDatabasePersistenceSPI.getProvider(persistenceId)
        persistence = persistenceSPI.newPersistence(javaRuntime, settings)
        featuresRegistry = FeaturesRegistry(settings.features).apply { bind(this@JcDatabaseImpl) }
        locationsRegistry = persistenceSPI.newLocationsRegistry(this)
    }

    override val locations: List<RegisteredLocation> get() = locationsRegistry.actualLocations

    suspend fun restore() {
        featuresRegistry.broadcast(JcInternalSignal.BeforeIndexing(settings.persistenceClearOnStart ?: false))
        persistence.setup()
        locationsRegistry.cleanup()
        val runtime = JavaRuntime(settings.jre).allLocations
        locationsRegistry.setup(runtime).new.process(false)
        locationsRegistry.registerIfNeeded(
            settings.predefinedDirOrJars.filter { it.exists() }
                .map { it.asByteCodeLocation(javaRuntime.version, isRuntime = false) }
        ).new.process(true)
    }

    private fun List<JcClasspathFeature>?.appendBuiltInFeatures(): List<JcClasspathFeature> {
        if (this != null && any { it is ClasspathCache }) {
            return this + listOf(KotlinMetadata, MethodInstructionsFeature(settings.keepLocalVariableNames))
        }
        return listOf(
            ClasspathCache(settings.cacheSettings),
            KotlinMetadata,
            MethodInstructionsFeature(settings.keepLocalVariableNames)
        ) + orEmpty()
    }

    override suspend fun classpath(dirOrJars: List<File>, features: List<JcClasspathFeature>?): JcClasspath {
        assertNotClosed()
        val existingLocations = dirOrJars.filterExisting().map { it.asByteCodeLocation(javaRuntime.version) }
        val processed = locationsRegistry.registerIfNeeded(existingLocations)
            .also { it.new.process(true) }.registered + locationsRegistry.runtimeLocations
        return classpathOf(processed, features)
    }

    override fun classpathOf(locations: List<RegisteredLocation>, features: List<JcClasspathFeature>?): JcClasspath {
        return JcClasspathImpl(
            locationsRegistry.newSnapshot(locations),
            this,
            features.appendBuiltInFeatures(),
            classesVfs
        )
    }

    fun new(cp: JcClasspathImpl): JcClasspath {
        assertNotClosed()
        return JcClasspathImpl(
            locationsRegistry.newSnapshot(cp.registeredLocations),
            cp.db,
            cp.features,
            classesVfs
        )
    }

    override val runtimeVersion: JavaVersion
        get() = javaRuntime.version

    override suspend fun load(dirOrJar: File) = apply {
        assertNotClosed()
        load(listOf(dirOrJar))
    }

    override suspend fun load(dirOrJars: List<File>) = apply {
        assertNotClosed()
        loadLocations(dirOrJars.filterExisting().map { it.asByteCodeLocation(javaRuntime.version) })
    }

    override suspend fun loadLocations(locations: List<JcByteCodeLocation>) = apply {
        assertNotClosed()
        locationsRegistry.registerIfNeeded(locations).new.process(true)
    }

    private suspend fun List<RegisteredLocation>.process(createIndexes: Boolean): List<RegisteredLocation> {
        withContext(Dispatchers.IO) {
            map { location ->
                async {
                    // here something may go wrong
                    location.lazySources.forEach {
                        classesVfs.addClass(it)
                    }
                }
            }
        }.awaitAll()
        val backgroundJobId = jobId.incrementAndGet()
        backgroundJobs[backgroundJobId] = backgroundScope.launch {
            val parentScope = this
            map { location ->
                async {
                    val sources = location.sources
                    parentScope.ifActive { persistence.persist(location, sources) }
                    parentScope.ifActive {
                        classesVfs.visit(
                            RemoveLocationsVisitor(
                                listOf(location),
                                settings.byteCodeSettings.prefixes
                            )
                        )
                    }
                    parentScope.ifActive { featuresRegistry.index(location, sources) }
                }
            }.joinAll()
            if (createIndexes) {
                persistence.createIndexes()
            }
            locationsRegistry.afterProcessing(this@process)
            backgroundJobs.remove(backgroundJobId)
        }
        return this
    }

    override suspend fun refresh() {
        awaitBackgroundJobs()
        locationsRegistry.refresh().new.process(true)
        val result = locationsRegistry.cleanup()
        classesVfs.visit(RemoveLocationsVisitor(result.outdated, settings.byteCodeSettings.prefixes))
    }

    override suspend fun rebuildFeatures() {
        awaitBackgroundJobs()
        featuresRegistry.broadcast(JcInternalSignal.Drop)

        withContext(Dispatchers.IO) {
            val locations = locationsRegistry.actualLocations
            val parentScope = this
            locations.map {
                async {
                    val addedClasses = persistence.findClassSources(this@JcDatabaseImpl, it)
                    parentScope.ifActive { featuresRegistry.index(it, addedClasses) }
                }
            }.joinAll()
        }
    }

    override fun watchFileSystemChanges(): JcDatabase {
        val delay = settings.watchFileSystemDelay?.toLong()
        if (delay != null) { // just paranoid check
            backgroundScope.launch {
                while (true) {
                    delay(delay)
                    refresh()
                }
            }
        }
        return this
    }

    override suspend fun awaitBackgroundJobs() {
        backgroundJobs.values.joinAll()
    }

    override val features: List<JcFeature<*, *>>
        get() = featuresRegistry.features

    suspend fun afterStart() {
        hooks.forEach { it.afterStart() }
    }

    override fun close() {
        isClosed.set(true)
        locationsRegistry.close()
        backgroundJobs.values.forEach {
            it.cancel()
        }
        runBlocking {
            awaitBackgroundJobs()
        }
        backgroundJobs.clear()
        classesVfs.close()
        backgroundScope.cancel()
        persistence.close()
        hooks.forEach { it.afterStop() }
    }

    private fun assertNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("Database is already closed")
        }
    }

    private inline fun CoroutineScope.ifActive(action: () -> Unit) {
        if (isActive) {
            action()
        }
    }

}
