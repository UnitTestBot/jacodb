package org.utbot.jcdb.api

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.InputStream
import java.io.OutputStream

/**
 * index builder
 */
interface ByteCodeIndexBuilder<T, INDEX : Index<T>> {

    suspend fun index(classNode: ClassNode)

    suspend fun index(classNode: ClassNode, methodNode: MethodNode)

    fun build(): INDEX

}

interface Index<T> {

    suspend fun query(term: String): Sequence<T>
}

interface Feature<T, INDEX : Index<T>> {

    val key: String

    fun newBuilder(): ByteCodeIndexBuilder<T, INDEX>

    fun serialize(index: INDEX, out: OutputStream)

    fun deserialize(stream: InputStream): INDEX?

}
