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

package org.utbot.jacodb.impl.vfs

import org.utbot.jacodb.api.RegisteredLocation

abstract class AbstractVfsItem<T : AbstractVfsItem<T>>(open val name: String?, val parent: T?) {

    val fullName: String by lazy {
        val reversedNames = arrayListOf<String>()
        var node: AbstractVfsItem<*>? = this
        while (node != null) {
            node.name?.let {
                reversedNames.add(it)
            }
            node = node.parent
        }
         reversedNames.reversed().joinToString(".")
    }

}

interface VfsVisitor {

    fun visitPackage(packageItem: PackageVfsItem) {}
}

class RemoveLocationsVisitor(private val locations: List<RegisteredLocation>) : VfsVisitor {

    override fun visitPackage(packageItem: PackageVfsItem) {
        if (packageItem.fullName.startsWith("java.")) {
            return
        }
        locations.forEach {
            packageItem.removeClasses(it.id)
        }
    }
}