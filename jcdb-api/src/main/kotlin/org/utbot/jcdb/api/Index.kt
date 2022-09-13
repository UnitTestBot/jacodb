package org.utbot.jcdb.api

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

/**
 * index builder
 */
interface ByteCodeIndexer {

    suspend fun index(classNode: ClassNode)

    suspend fun index(classNode: ClassNode, methodNode: MethodNode)

    fun flush()
}

interface JCDBFeature<REQ, RES> {

    val jcdb: JCDB

    val key: String

    suspend fun query(req: REQ): Sequence<RES>

    fun newIndexer(location: ByteCodeLocation): ByteCodeIndexer

    fun onLocationRemoved(location: ByteCodeLocation)

    val persistence: FeaturePersistence?

}

interface Feature<REQ, RES> {

    val key: String

    fun featureOf(jcdb: JCDB): JCDBFeature<REQ, RES>

}

interface FeaturePersistence {

    val jcdbPersistence: JCDBPersistence

    /** executed after jcdb instance creating and before processing of bytecode */
    fun beforeIndexing(clearOnStart: Boolean)

    /** can be used to create database indexes */
    fun onBatchLoadingEnd()
}
