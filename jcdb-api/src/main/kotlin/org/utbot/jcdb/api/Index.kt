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

    suspend fun query(jcdb: JCDB, req: REQ): Sequence<RES>

    fun newIndexer(jcdb: JCDB, location: RegisteredLocation): ByteCodeIndexer

    fun onSignal(signal: JcSignal)

}


sealed class JcSignal(val jcdb: JCDB) {

    /** can be used for creating persistence scheme */
    class BeforeIndexing(jcdb: JCDB, val clearOnStart: Boolean) : JcSignal(jcdb)
    /** can be used to create persistence indexes after data batch upload */
    class AfterIndexing(jcdb: JCDB) : JcSignal(jcdb)
    /** can be used for cleanup index data when location is removed */
    class LocationRemoved(jcdb: JCDB, val location: RegisteredLocation) : JcSignal(jcdb)
    class ProcessLocation(jcdb: JCDB, val location: RegisteredLocation, ) : JcSignal(jcdb)

}


suspend fun <REQ, RES> JcClasspath.query(feature: Feature<REQ, RES>, req: REQ): Sequence<RES> {
    return feature.query(db, req)
}