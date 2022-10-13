package org.utbot.jcdb.api

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

/** index builder */
interface ByteCodeIndexer {

    suspend fun index(classNode: ClassNode)

    suspend fun index(classNode: ClassNode, methodNode: MethodNode)

    fun flush()
}

interface Feature<REQ, RES> {

    val key: String

    suspend fun query(jcdb: JCDB, req: REQ): Sequence<RES>

    fun newIndexer(jcdb: JCDB, location: RegisteredLocation): ByteCodeIndexer

    /** this method will be called when location is removed */
    fun onRemoved(jcdb: JCDB, location: RegisteredLocation)

    /** executed after jcdb instance creating and before processing of bytecode */
    fun beforeIndexing(jcdb: JCDB, clearOnStart: Boolean)

    /** can be used to create database indexes */
    fun afterIndexing(jcdb: JCDB)

}


suspend fun <REQ, RES> JcClasspath.query(feature: Feature<REQ, RES>, req: REQ): Sequence<RES> {
    return feature.query(db, req)
}