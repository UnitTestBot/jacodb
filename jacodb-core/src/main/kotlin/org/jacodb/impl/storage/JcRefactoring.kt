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

package org.jacodb.impl.storage

import mu.KLogging
import org.jacodb.api.jvm.JCDBContext
import org.jacodb.impl.features.Builders
import org.jacodb.impl.features.Usages
import org.jacodb.impl.storage.jooq.tables.references.APPLICATIONMETADATA
import org.jacodb.impl.storage.jooq.tables.references.REFACTORINGS
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

private const val REFACTORING_TYPE = "Refactoring"

abstract class JcRefactoring {

    val name: String = javaClass.name

    /**
     * executed inside transaction
     */
    abstract fun run(context: JCDBContext)
}

class JcRefactoringChain(private val chain: List<JcRefactoring>) {

    companion object : KLogging()

    @OptIn(ExperimentalTime::class)
    fun execute(context: JCDBContext) {
        context.execute(
            sqlAction = { jooq ->
                val applied = hashSetOf<String>()
                try {
                    applied.addAll(jooq.select(REFACTORINGS.NAME).from(REFACTORINGS).fetchArray(REFACTORINGS.NAME))
                } catch (e: Exception) {
                    logger.info("fail to fetch applied refactorings")
                }
                chain.forEach { ref ->
                    jooq.connection {
                        if (!applied.contains(ref.name)) {
                            val time = measureTime {
                                ref.run(context)
                                jooq.insertInto(REFACTORINGS).set(REFACTORINGS.NAME, ref.name).execute()
                            }
                            logger.info("Refactoring ${ref.name} took $time msc")
                        }
                    }
                }
            },
            noSqlAction = { txn ->
                chain.forEach { refactoring ->
                    val refactoringName = refactoring.name
                    if (txn.find(REFACTORING_TYPE, "name", refactoringName).isEmpty) {
                        val time = measureTime {
                            refactoring.run(context)
                            txn.newEntity(REFACTORING_TYPE)["name"] = refactoringName
                        }
                        logger.info("Refactoring $refactoringName took $time msc")
                    }
                }
            }
        )
    }
}

class AddAppMetadataAndRefactoring : JcRefactoring() {

    override fun run(context: JCDBContext) {
        // This refactoring is applicable only for SQL context
        if (context.isSqlContext) {
            val jooq = context.dslContext
            jooq.createTableIfNotExists(APPLICATIONMETADATA)
                .column(APPLICATIONMETADATA.VERSION)
                .execute()
            jooq.createTableIfNotExists(REFACTORINGS)
                .column(REFACTORINGS.NAME)
                .execute()
        }
    }
}

class UpdateUsageAndBuildersSchemeRefactoring : JcRefactoring() {

    override fun run(context: JCDBContext) {
        Usages.create(context, true)
        Builders.create(context, true)
    }
}
