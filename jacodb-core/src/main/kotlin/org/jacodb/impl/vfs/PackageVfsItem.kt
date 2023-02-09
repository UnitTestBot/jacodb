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

package org.jacodb.impl.vfs

import java.util.concurrent.ConcurrentHashMap

class PackageVfsItem(folderName: String?, parent: PackageVfsItem?) :
    AbstractVfsItem<PackageVfsItem>(folderName, parent) {

    // folderName -> subpackage
    internal var subpackages = ConcurrentHashMap<String, PackageVfsItem>()

    // simpleName -> (locationId -> node)
    internal var classes = ConcurrentHashMap<String, ConcurrentHashMap<Long, ClassVfsItem>>()

    fun findPackageOrNull(subfolderName: String): PackageVfsItem? {
        return subpackages[subfolderName]
    }

    fun firstClassOrNull(className: String, locationId: Long): ClassVfsItem? {
        return classes[className]?.get(locationId)
    }

    fun firstClassOrNull(className: String, predicate: (Long) -> Boolean): ClassVfsItem? {
        val locationsClasses = classes.get(className) ?: return null
        return locationsClasses.asSequence().firstOrNull { predicate(it.key) }?.value
    }

    fun visit(visitor: VfsVisitor) {
        visitor.visitPackage(this)
        subpackages.values.forEach {
            it.visit(visitor)
        }
    }

    fun removeClasses(locationId: Long) {
        classes.values.forEach {
            it.remove(locationId)
        }
    }
}