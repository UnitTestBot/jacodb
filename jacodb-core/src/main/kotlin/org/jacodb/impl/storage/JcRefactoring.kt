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
import org.jacodb.impl.features.Builders
import org.jacodb.impl.features.Usages
import org.jacodb.impl.storage.jooq.tables.references.APPLICATIONMETADATA
import org.jacodb.impl.storage.jooq.tables.references.REFACTORINGS
import org.jooq.DSLContext
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

abstract class JcRefactoring {

    val name: String = javaClass.name

    /**
     * executed inside transaction
     */
    abstract fun run(jooq: DSLContext)

}

class JcRefactoringChain(private val chain: List<JcRefactoring>) {

    companion object : KLogging()

    private val applied = hashSetOf<String>()

    @OptIn(ExperimentalTime::class)
    fun execute(jooq: DSLContext) {
        try {
            applied.addAll(jooq.select(REFACTORINGS.NAME).from(REFACTORINGS).fetchArray(REFACTORINGS.NAME))
        } catch (e: Exception) {
            logger.info("fail to fetch applied refactorings")
        }

        chain.forEach { ref ->
            jooq.connection {
                if (!applied.contains(ref.name)) {
                    val time = measureTime {
                        ref.run(jooq)
                        jooq.insertInto(REFACTORINGS).set(REFACTORINGS.NAME, ref.name).execute()
                    }
                    logger.info("Refactoring ${ref.name} took $time msc")
                }
            }
        }
    }

}

class AddAppmetadataAndRefactoring : JcRefactoring() {

    override fun run(jooq: DSLContext) {
        jooq.createTableIfNotExists(APPLICATIONMETADATA)
            .column(APPLICATIONMETADATA.VERSION)
            .execute()

        jooq.createTableIfNotExists(REFACTORINGS)
            .column(REFACTORINGS.NAME)
            .execute()
    }
}

class UpdateUsageAndBuildersSchemeRefactoring : JcRefactoring() {

    override fun run(jooq: DSLContext) {
        Usages.create(jooq, true)
        Builders.create(jooq, true)
    }
}
