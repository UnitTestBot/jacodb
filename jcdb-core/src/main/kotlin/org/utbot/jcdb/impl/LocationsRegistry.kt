/**
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
package org.utbot.jcdb.impl

import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.RegisteredLocation
import java.io.Closeable

interface LocationsRegistry : Closeable {
    // all locations
    val actualLocations: List<RegisteredLocation>
    val runtimeLocations: List<RegisteredLocation>

    fun cleanup(): CleanupResult
    fun refresh(): RefreshResult
    fun setup(runtimeLocations: List<JcByteCodeLocation>): RegistrationResult

    fun registerIfNeeded(locations: List<JcByteCodeLocation>): RegistrationResult
    fun afterProcessing(locations: List<RegisteredLocation>)

    fun newSnapshot(classpathSetLocations: List<RegisteredLocation>): LocationsRegistrySnapshot

    fun close(snapshot: LocationsRegistrySnapshot)

    fun RegisteredLocation.hasReferences(snapshots: Set<LocationsRegistrySnapshot>): Boolean {
        return snapshots.isNotEmpty() && snapshots.any { it.ids.contains(id) }
    }

}

class RegistrationResult(val registered: List<RegisteredLocation>, val new: List<RegisteredLocation>)
class RefreshResult(val new: List<RegisteredLocation>)
class CleanupResult(val outdated: List<RegisteredLocation>)

open class LocationsRegistrySnapshot(
    private val registry: LocationsRegistry,
    val locations: List<RegisteredLocation>
) : Closeable {

    val ids = locations.map { it.id }.toHashSet()

    override fun close() = registry.close(this)

}