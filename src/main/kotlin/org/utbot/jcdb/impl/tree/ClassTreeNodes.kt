package org.utbot.jcdb.impl.tree

import org.utbot.jcdb.api.ByteCodeLocation

abstract class AbstractNode<T: AbstractNode<T>>(open val name: String?, val parent: T?) {

    val fullName: String by lazy(LazyThreadSafetyMode.NONE) {
        val reversedNames = arrayListOf(name)
        var node: T? = parent
        while (node != null) {
            node.name?.let {
                reversedNames.add(it)
            }
            node = node.parent
        }
        reversedNames.reversed().joinToString(".")
    }
}

interface NodeVisitor {

    fun visitPackageNode(packageNode: PackageNode) {}
}

class RemoveLocationsVisitor(private val locations: Set<ByteCodeLocation>) : NodeVisitor {
    override fun visitPackageNode(packageNode: PackageNode) {
        locations.forEach {
            packageNode.dropLocation(it.id)
        }
    }

}