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

package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.ClassSource
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.vfs.PersistentByteCodeLocation

class ClassSourceImpl(
    override val location: RegisteredLocation,
    override val className: String,
    override val byteCode: ByteArray
) : ClassSource

class LazyClassSourceImpl(
    override val location: RegisteredLocation,
    override val className: String
) : ClassSource {

    override val byteCode by lazy {
        location.jcLocation?.resolve(className) ?: className.throwClassNotFound()
    }
}

class PersistenceClassSource(
    private val classpath: JcClasspath,
    override val className: String,
    val classId: Long,
    val locationId: Long,
    private val cachedByteCode: ByteArray? = null
) : ClassSource {

    private constructor(persistenceClassSource: PersistenceClassSource, byteCode: ByteArray) : this(
        persistenceClassSource.classpath,
        persistenceClassSource.className,
        persistenceClassSource.classId,
        persistenceClassSource.locationId,
        byteCode
    )

    override val location = PersistentByteCodeLocation(classpath, locationId)

    override val byteCode by lazy {
        cachedByteCode ?: classpath.db.persistence.findBytecode(classId)
    }

    fun bind(byteCode: ByteArray?) = when {
        byteCode != null -> PersistenceClassSource(this, byteCode)
        else -> this
    }
}
