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

package org.jacodb.api.analysis

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathTask
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcGraph

interface JcAnalysisPlatform : JcClasspathTask {

    val classpath: JcClasspath
    val features: List<JcAnalysisFeature>

    fun flowGraph(method: JcMethod): JcGraph

    suspend fun collect()
    suspend fun asyncCollect() = GlobalScope.future { collect() }

}


