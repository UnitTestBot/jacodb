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

package org.utbot.jacodb.api

import org.jooq.DSLContext
import org.objectweb.asm.tree.ClassNode

/** index builder */
interface ByteCodeIndexer {

    fun index(classNode: ClassNode)

    fun flush(jooq: DSLContext)
}

interface JcFeature<REQ, RES> {

    suspend fun query(classpath: JcClasspath, req: REQ): Sequence<RES>

    fun newIndexer(jcdb: JcDatabase, location: RegisteredLocation): ByteCodeIndexer

    fun onSignal(signal: JcSignal)

}


sealed class JcSignal(val jcdb: JcDatabase) {

    /** can be used for creating persistence scheme */
    class BeforeIndexing(jcdb: JcDatabase, val clearOnStart: Boolean) : JcSignal(jcdb)
    /** can be used to create persistence indexes after data batch upload */
    class AfterIndexing(jcdb: JcDatabase) : JcSignal(jcdb)
    /** can be used for cleanup index data when location is removed */
    class LocationRemoved(jcdb: JcDatabase, val location: RegisteredLocation) : JcSignal(jcdb)
    /**
     * rebuild all
     */
    class Drop(jcdb: JcDatabase) : JcSignal(jcdb)

    /**
     * database is closed
     */
    class Closed(jcdb: JcDatabase) : JcSignal(jcdb)

}


suspend fun <REQ, RES> JcClasspath.query(feature: JcFeature<REQ, RES>, req: REQ): Sequence<RES> {
    return feature.query(this, req)
}