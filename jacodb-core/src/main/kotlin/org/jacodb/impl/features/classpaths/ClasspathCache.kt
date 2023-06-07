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

package org.jacodb.impl.features.classpaths

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheStats
import mu.KLogging
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathExtFeature
import org.jacodb.api.JcClasspathExtFeature.JcResolvedClassResult
import org.jacodb.api.JcClasspathExtFeature.JcResolvedTypeResult
import org.jacodb.api.JcFeatureEvent
import org.jacodb.api.JcMethod
import org.jacodb.api.JcMethodExtFeature
import org.jacodb.api.JcMethodExtFeature.JcFlowGraphResult
import org.jacodb.api.JcMethodExtFeature.JcInstListResult
import org.jacodb.api.JcMethodExtFeature.JcRawInstListResult
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.impl.JcCacheSegmentSettings
import org.jacodb.impl.JcCacheSettings
import org.jacodb.impl.ValueStoreType
import org.jacodb.impl.features.classpaths.AbstractJcInstResult.JcFlowGraphResultImpl
import org.jacodb.impl.features.classpaths.AbstractJcInstResult.JcInstListResultImpl
import org.jacodb.impl.features.classpaths.AbstractJcInstResult.JcRawInstListResultImpl
import org.jacodb.impl.features.classpaths.AbstractJcResolvedResult.JcResolvedTypeResultImpl
import java.text.NumberFormat


/**
 * any class cache should extend this class
 */
open class ClasspathCache(settings: JcCacheSettings) : JcClasspathExtFeature, JcMethodExtFeature {

    companion object : KLogging()

    private val classesCache = segmentBuilder(settings.classes)
        .build<String, JcResolvedClassResult>()

    private val typesCache = segmentBuilder(settings.types)
        .build<String, JcResolvedTypeResult>()

    private val rawInstCache = segmentBuilder(settings.rawInstLists)
        .build<JcMethod, JcInstList<JcRawInst>>()

    private val instCache = segmentBuilder(settings.instLists)
        .build<JcMethod, JcInstList<JcInst>>()

    private val cfgCache = segmentBuilder(settings.flowGraphs)
        .build<JcMethod, JcGraph>()


    override fun tryFindClass(classpath: JcClasspath, name: String): JcResolvedClassResult? {
        return classesCache.getIfPresent(name)
    }

    override fun tryFindType(classpath: JcClasspath, name: String): JcResolvedTypeResult? {
        return typesCache.getIfPresent(name)
    }

    override fun flowGraph(method: JcMethod) = cfgCache.getIfPresent(method)?.let {
        JcFlowGraphResultImpl(method, it)
    }
    override fun instList(method: JcMethod) = instCache.getIfPresent(method)?.let {
        JcInstListResultImpl(method, it)
    }
    override fun rawInstList(method: JcMethod) = rawInstCache.getIfPresent(method)?.let {
        JcRawInstListResultImpl(method, it)
    }

    override fun on(event: JcFeatureEvent) {
        when (val result = event.result) {
            is JcResolvedClassResult -> {
                classesCache.put(result.name, result)
                typesCache.put(result.name, JcResolvedTypeResultImpl(result.name, null))
            }

            is JcResolvedTypeResult -> {
                val found = result.type
                if (found != null && found is JcClassType) {
                    typesCache.put(result.name, result)
                }
            }

            is JcFlowGraphResult -> {
                cfgCache.put(result.method, result.flowGraph)
            }
            is JcInstListResult -> {
                instCache.put(result.method, result.instList)
            }
            is JcRawInstListResult -> {
                rawInstCache.put(result.method, result.rawInstList)
            }
        }
    }

    protected fun segmentBuilder(settings: JcCacheSegmentSettings)
            : CacheBuilder<Any, Any> {
        val maxSize = settings.maxSize
        val expiration = settings.expiration

        return CacheBuilder.newBuilder()
            .expireAfterAccess(expiration)
            .recordStats()
            .maximumSize(maxSize).let {
                when (settings.valueStoreType) {
                    ValueStoreType.WEAK -> it.weakValues()
                    ValueStoreType.SOFT -> it.softValues()
                    else -> it
                }
            }
    }

    open fun stats(): Map<String, CacheStats> = buildMap {
        this["classes"] = classesCache.stats()
        this["types"] = typesCache.stats()
        this["cfg"] = cfgCache.stats()
        this["raw-instructions"] = rawInstCache.stats()
        this["instructions"] = instCache.stats()
    }

    open fun dumpStats() {
        stats().entries.toList()
            .sortedBy { it.key }
            .forEach { (key, stat) ->
                logger.info(
                    "$key cache hit rate: ${
                        stat.hitRate().forPercentages()
                    }, total count ${stat.requestCount()}"
                )
            }
    }

    protected fun Double.forPercentages(): String {
        return NumberFormat.getPercentInstance().format(this)
    }
}