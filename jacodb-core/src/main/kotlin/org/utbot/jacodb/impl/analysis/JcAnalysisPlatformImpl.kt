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

package org.utbot.jacodb.impl.analysis

import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcClassProcessingTask
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.analysis.JcAnalysisFeature
import org.utbot.jacodb.api.analysis.JcAnalysisPlatform
import org.utbot.jacodb.api.analysis.JcCollectingAnalysisFeature
import org.utbot.jacodb.api.cfg.JcGraph

open class JcAnalysisPlatformImpl(
    override val classpath: JcClasspath,
    override val features: List<JcAnalysisFeature> = emptyList()
) : JcAnalysisPlatform, JcClassProcessingTask {

    private val collectors = features.filterIsInstance<JcCollectingAnalysisFeature>()

    override fun flowGraph(method: JcMethod): JcGraph {
        var index = 0
        var maybeCached: JcGraph? = null
        features.forEachIndexed { i, feature ->
            maybeCached = feature.flowOf(method)
            if (maybeCached != null) {
                index = i
            }
        }
        val initial = maybeCached ?: method.flowGraph()
        return features.drop(index).fold(initial) { value, feature ->
            feature.transform(value)
        }
    }


    override suspend fun collect() {
        if (collectors.isNotEmpty()) {
            classpath.execute(this)
        }
    }

    override fun process(clazz: JcClassOrInterface) {
        clazz.declaredMethods.forEach { method ->
            val flowGraph = flowGraph(method)
            collectors.forEach { feature ->
                feature.collect(method, flowGraph)
            }
        }
    }
}