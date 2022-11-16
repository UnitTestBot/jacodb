package org.utbot.jcdb.api

import org.jooq.DSLContext
import org.objectweb.asm.tree.ClassNode

/** index builder */
interface ByteCodeIndexer {

    fun index(classNode: ClassNode)

    fun flush(jooq: DSLContext)
}

interface JcFeature<REQ, RES> {

    suspend fun query(classpath: JcClasspath, req: REQ): Sequence<RES>

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
    /**
     * rebuild all
     */
    class Drop(jcdb: JCDB) : JcSignal(jcdb)

}


suspend fun <REQ, RES> JcClasspath.query(feature: JcFeature<REQ, RES>, req: REQ): Sequence<RES> {
    return feature.query(this, req)
}