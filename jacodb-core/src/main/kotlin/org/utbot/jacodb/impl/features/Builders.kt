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

package org.utbot.jacodb.impl.features

import org.jooq.DSLContext
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.utbot.jacodb.api.ByteCodeIndexer
import org.utbot.jacodb.api.ClassSource
import org.utbot.jacodb.api.JCDB
import org.utbot.jacodb.api.JCDBPersistence
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcFeature
import org.utbot.jacodb.api.JcSignal
import org.utbot.jacodb.api.RegisteredLocation
import org.utbot.jacodb.api.ext.jvmPrimitiveNames
import org.utbot.jacodb.impl.fs.PersistenceClassSource
import org.utbot.jacodb.impl.fs.className
import org.utbot.jacodb.impl.storage.executeQueries
import org.utbot.jacodb.impl.storage.jooq.tables.references.BUILDERS
import org.utbot.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.utbot.jacodb.impl.storage.jooq.tables.references.SYMBOLS
import org.utbot.jacodb.impl.storage.runBatch

private val MethodNode.isGetter: Boolean
    get() {
        return name.startsWith("get")
    }

private val Int.isPublic get() = this and Opcodes.ACC_PUBLIC != 0
private val Int.isStatic get() = this and Opcodes.ACC_STATIC != 0

private data class BuilderMethod(
    val callerClass: String,
    val methodOffset: Int,
    val priority: Int
)

class BuildersIndexer(val persistence: JCDBPersistence, private val location: RegisteredLocation) :
    ByteCodeIndexer {

    // class -> (caller_class, offset, priority)
    private val potentialBuilders = hashMapOf<String, HashSet<BuilderMethod>>()

    override fun index(classNode: ClassNode) {
        val callerClass = classNode.name
        classNode.methods.forEachIndexed { index, methodNode ->
            val isStatic = methodNode.access.isStatic
            if (methodNode.access.isPublic && !methodNode.isGetter) {
                val returnType = Type.getMethodType(methodNode.desc).returnType.internalName
                if (
                    !jvmPrimitiveNames.contains(returnType) && // not interesting in primitives
                    !returnType.startsWith("[") && // not interesting in arrays
                    !returnType.startsWith("java/") // not interesting in java package classes
                ) {
                    val noParams = Type.getArgumentTypes(methodNode.desc).isNullOrEmpty()
                    val isBuildName = methodNode.name.equals("build")
                    val priority = when {
                        isStatic && noParams && returnType == callerClass -> 15
                        isStatic && noParams -> 10
                        isBuildName && noParams -> 7
                        isStatic -> 5
                        isBuildName -> 3
                        else -> 0
                    }
                    potentialBuilders.getOrPut(returnType) { hashSetOf() }
                        .add(BuilderMethod(callerClass, index, priority))
                }
            }
        }
    }


    override fun flush(jooq: DSLContext) {
        jooq.connection { conn ->
            conn.runBatch(BUILDERS) {
                potentialBuilders.forEach { (calleeClass, builders) ->
                    val calleeId = calleeClass.className.symbolId
                    builders.forEach {
                        val (callerClass, offset, priority) = it
                        val callerId = callerClass.className.symbolId
                        setLong(1, calleeId)
                        setLong(2, callerId)
                        setInt(3, priority)
                        setInt(4, offset)
                        setLong(5, location.id)
                        addBatch()
                    }
                }
            }
        }
    }

    private inline val String.symbolId
        get() = persistence.findSymbolId(this) ?: throw IllegalStateException("Id not found for name: $this")
}

data class BuildersResponse(
    val methodOffset: Int,
    val priority: Int,
    val source: ClassSource
)

object Builders : JcFeature<Set<String>, BuildersResponse> {

    private val createScheme = """
        CREATE TABLE IF NOT EXISTS "Builders"(
            "class_symbol_id"               BIGINT NOT NULL,
            "builder_class_symbol_id"       BIGINT NOT NULL,
            "priority"                      INTEGER NOT NULL,
            "offset"      					INTEGER NOT NULL,
            "location_id"                   BIGINT NOT NULL,
            CONSTRAINT "fk_class_symbol_id" FOREIGN KEY ("class_symbol_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE,
            CONSTRAINT "fk_builder_class_symbol_id" FOREIGN KEY ("builder_class_symbol_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE,
            CONSTRAINT "fk_location_id" FOREIGN KEY ("location_id") REFERENCES "BytecodeLocations" ("id") ON DELETE CASCADE
        );
    """.trimIndent()

    private val createIndex = """
		    CREATE INDEX IF NOT EXISTS 'Builders search' ON Builders(location_id, class_symbol_id, priority);
            CREATE INDEX IF NOT EXISTS 'Builders sorting' ON Builders(priority);
            CREATE INDEX IF NOT EXISTS 'Builders join' ON Builders(builder_class_symbol_id);
    """.trimIndent()

    private val dropScheme = """
            DROP TABLE IF EXISTS "Builders";
            DROP INDEX IF EXISTS "Builders search";
            DROP INDEX IF EXISTS "Builders sorting";
            DROP INDEX IF EXISTS "Builders join";
    """.trimIndent()

    override fun onSignal(signal: JcSignal) {
        when (signal) {
            is JcSignal.BeforeIndexing -> {
                signal.jcdb.persistence.write {
                    if (signal.clearOnStart) {
                        it.executeQueries(dropScheme)
                    }
                    it.executeQueries(createScheme)
                }
            }

            is JcSignal.LocationRemoved -> {
                signal.jcdb.persistence.write {
                    it.deleteFrom(BUILDERS).where(BUILDERS.LOCATION_ID.eq(signal.location.id)).execute()
                }
            }

            is JcSignal.AfterIndexing -> {
                signal.jcdb.persistence.write {
                    it.executeQueries(createIndex)
                }
            }

            is JcSignal.Drop -> {
                signal.jcdb.persistence.write {
                    it.deleteFrom(BUILDERS).execute()
                }
            }

            else -> Unit
        }
    }

    override suspend fun query(classpath: JcClasspath, req: Set<String>): Sequence<BuildersResponse> {
        return syncQuery(classpath, req)
    }

    fun syncQuery(classpath: JcClasspath, req: Set<String>): Sequence<BuildersResponse> {
        val locationIds = classpath.registeredLocations.map { it.id }
        val persistence = classpath.db.persistence
        val classNameIds = req.map { persistence.findSymbolId(it) }
        return sequence {
            val result = persistence.read { jooq ->
                jooq.select(BUILDERS.OFFSET, SYMBOLS.NAME, CLASSES.ID, CLASSES.LOCATION_ID, BUILDERS.PRIORITY)
                    .from(BUILDERS)
                    .join(SYMBOLS).on(SYMBOLS.ID.eq(BUILDERS.BUILDER_CLASS_SYMBOL_ID))
                    .join(CLASSES).on(CLASSES.NAME.eq(BUILDERS.BUILDER_CLASS_SYMBOL_ID))
                    .where(
                        BUILDERS.CLASS_SYMBOL_ID.`in`(classNameIds).and(BUILDERS.LOCATION_ID.`in`(locationIds))
                    )
                    .limit(100)
                    .fetch()
                    .mapNotNull { (offset, className, classId, locationId, priority) ->
                        BuildersResponse(
                            source = PersistenceClassSource(
                                classpath,
                                locationId = locationId!!,
                                classId = classId!!,
                                className = className!!,
                            ),
                            methodOffset = offset!!,
                            priority = priority ?: 0
                        )
                    }.sortedByDescending { it.priority }
            }
            yieldAll(result)
        }

    }

    override fun newIndexer(jcdb: JCDB, location: RegisteredLocation) = BuildersIndexer(jcdb.persistence, location)


}