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

package org.utbot.jacodb.impl.analysis.features

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.analysis.JcAnalysisFeature
import org.utbot.jacodb.api.cfg.JcGraph

class JcCacheGraphFeature(maxSize: Long) : JcAnalysisFeature {

    private val cache: LoadingCache<JcMethod, JcGraph> = CacheBuilder.newBuilder()
        .softValues()
        .maximumSize(maxSize)
        .build(object : CacheLoader<JcMethod, JcGraph>() {
            override fun load(method: JcMethod): JcGraph {
                return method.flowGraph()
            }
        })

    override fun flowOf(method: JcMethod): JcGraph? {
        return cache.getIfPresent(method)
    }

    override fun transform(graph: JcGraph): JcGraph {
        cache.put(graph.method, graph)
        return graph
    }
}