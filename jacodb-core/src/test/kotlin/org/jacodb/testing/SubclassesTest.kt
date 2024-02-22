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

package org.jacodb.testing

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.JAVA_OBJECT
import org.jacodb.impl.features.hierarchyExt
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@LifecycleTest
class SubclassesTest : BaseTest() {

    companion object : WithGlobalDB()

    private val withDB = WithDB()

    private val anotherDb = withDB.db
    private val anotherCp: JcClasspath by lazy {
        runBlocking {
            anotherDb.awaitBackgroundJobs()
            anotherDb.classpath(allClasspath)
        }
    }

    @Test
    fun `Object subclasses should be the same`() {
        runBlocking {
            val hierarchy = cp.hierarchyExt()
            val anotherHierarchy = anotherCp.hierarchyExt()
            Assertions.assertEquals(
                hierarchy.findSubClasses(JAVA_OBJECT, false, includeOwn = true).count(),
                anotherHierarchy.findSubClasses(JAVA_OBJECT, false, includeOwn = true).count()
            )
        }
    }

    @AfterEach
    fun `cleanup another db`() = runBlocking {
        withDB.cleanup()
    }

}
