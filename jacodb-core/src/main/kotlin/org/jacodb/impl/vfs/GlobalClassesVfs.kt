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

import org.jacodb.api.ClassSource
import org.jacodb.api.RegisteredLocation
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

open class GlobalClassesVfs : Closeable {

    private val rootItem = PackageVfsItem(null, null)

    private fun PackageVfsItem.findClassOrNew(
        simpleClassName: String,
        source: ClassSource
    ): ClassVfsItem {
        val nameIndex = classes.getOrPut(simpleClassName) {
            ConcurrentHashMap<Long, ClassVfsItem>()
        }
        return nameIndex.getOrPut(source.location.id) {
            ClassVfsItem(simpleClassName, this, source)
        }
    }

    private fun PackageVfsItem.findPackageOrNew(subfolderName: String): PackageVfsItem {
        return subpackages.getOrPut(subfolderName) {
            PackageVfsItem(subfolderName, this)
        }
    }

    fun addClass(source: ClassSource): ClassVfsItem {
        val splitted = source.className.splitted
        val simpleClassName = splitted[splitted.size - 1]
        if (splitted.size == 1) {
            return rootItem.findClassOrNew(simpleClassName, source)
        }
        var node = rootItem
        var index = 0
        while (index < splitted.size - 1) {
            val subfolderName = splitted[index]
            node = node.findPackageOrNew(subfolderName)
            index++
        }
        return node.findClassOrNew(simpleClassName, source)
    }

    fun findClassNodeOrNull(codeLocation: RegisteredLocation, fullName: String): ClassVfsItem? {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        return findPackage(splitted)?.firstClassOrNull(simpleClassName, codeLocation.id)
    }

    fun firstClassNodeOrNull(fullName: String, predicate: (Long) -> Boolean = { true }): ClassVfsItem? {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        return findPackage(splitted)?.firstClassOrNull(simpleClassName, predicate)
    }

    fun findClassNodes(fullName: String, predicate: (Long) -> Boolean = { true }): List<ClassVfsItem> {
        val splitted = fullName.splitted
        val simpleClassName = splitted[splitted.size - 1]
        return findPackage(splitted)?.findClasses(simpleClassName, predicate) ?: emptyList()
    }

    private fun findPackage(splitted: List<String>): PackageVfsItem? {
        var node: PackageVfsItem? = rootItem
        var index = 0
        while (index < (splitted.size - 1)) {
            if (node == null) {
                return null
            }
            val subfolderName = splitted[index]
            node = node.findPackageOrNull(subfolderName)
            index++
        }
        return node
    }

    fun visit(visitor: VfsVisitor) {
        rootItem.visit(visitor)
    }

    private val String.splitted get() = this.split(".")

    override fun close() {
        rootItem.classes.clear()
        rootItem.subpackages.clear()
    }

}
