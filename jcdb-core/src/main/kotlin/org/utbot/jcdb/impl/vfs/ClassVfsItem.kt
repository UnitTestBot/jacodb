package org.utbot.jcdb.impl.vfs

import org.utbot.jcdb.api.ClassSource

class ClassVfsItem(
    override val name: String,
    packageNode: PackageVfsItem,
    internal val source: ClassSource
) : AbstractVfsItem<PackageVfsItem>(name, packageNode) {

    override val fullName: String
        get() = super.fullName!!

    val location get() = source.location

}