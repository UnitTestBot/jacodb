package org.utbot.jcdb.impl.index

import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.ByteCodeLocationIndex
import org.utbot.jcdb.api.ByteCodeLocationIndexBuilder
import org.utbot.jcdb.impl.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

suspend fun <T> ByteCodeLocation.index(
    node: ClassNode,
    builderGetter: () -> ByteCodeLocationIndexBuilder<T>
): ByteCodeLocationIndex<T> {
    val builder = builderGetter()
    val asmNode = node.fullByteCode()
    builder.index(asmNode)
    asmNode.methods.forEach {
        builder.index(asmNode, it)
    }
    return builder.build(this)
}


object GlobalIds {

    private val counter = AtomicInteger()

    private val all = ConcurrentHashMap<String, Int>()
    private val reversed = ConcurrentHashMap<Int, String>()

    fun getId(name: String): Int {
        val id = all.getOrPut(name) {
            counter.incrementAndGet()
        }
        reversed[id] = name
        return id
    }

    fun getName(id: Int): String? {
        return reversed.get(id)
    }

}

