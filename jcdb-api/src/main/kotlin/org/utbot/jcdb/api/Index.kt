package org.utbot.jcdb.api

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

/**
 * index builder
 */
interface ByteCodeIndexBuilder<T, INDEX : Index<T, *>> {

    suspend fun index(classNode: ClassNode)

    suspend fun index(classNode: ClassNode, methodNode: MethodNode)

    fun build(): INDEX

}

interface Index<T, REQ: IndexRequest> {

    suspend fun query(req: REQ): Sequence<T>
}

interface IndexRequest

interface Feature<T, INDEX : Index<T, *>> {

    val key: String

    fun newBuilder(location: ByteCodeLocation): ByteCodeIndexBuilder<T, INDEX>

    fun persist(index: INDEX)

    fun onRestore(): INDEX?

}
