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

package org.jacodb.testing.tree

import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.LocationType
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.impl.storage.longHash
import java.io.File

open class DummyCodeLocation(private val name: String) : JcByteCodeLocation, RegisteredLocation {

    override val id: Long
        get() = name.longHash

    override val fileSystemId: String
        get() = name

    override val isRuntime: Boolean
        get() = false

    override val jcLocation: JcByteCodeLocation
        get() = this

    override val type = LocationType.APP

    override val classes: Map<String, ByteArray>?
        get() = null

    override val jarOrFolder: File
        get() = TODO("Not yet implemented")
    override val path: String
        get() = TODO("")

    override fun isChanged() = false

    override fun createRefreshed() = this

    override fun resolve(classFullName: String) = null

    override val classNames: Set<String>
        get() = emptySet()

}
