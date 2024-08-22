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

import mu.KLogging
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathExtFeature
import org.jacodb.api.jvm.JcClasspathExtFeature.JcResolvedClassResult
import org.jacodb.api.jvm.JcClasspathExtFeature.JcResolvedTypeResult
import org.jacodb.api.jvm.JcFeatureEvent
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.api.jvm.JcMethodExtFeature.JcFlowGraphResult
import org.jacodb.api.jvm.JcMethodExtFeature.JcInstListResult
import org.jacodb.api.jvm.JcMethodExtFeature.JcRawInstListResult
import org.jacodb.api.jvm.cfg.JcGraph
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.ext.JAVA_OBJECT
import org.jacodb.impl.JcCacheSegmentSettings
import org.jacodb.impl.JcCacheSettings
import org.jacodb.impl.caches.PluggableCache
import org.jacodb.impl.caches.PluggableCacheProvider
import org.jacodb.impl.caches.PluggableCacheStats
import org.jacodb.impl.features.classpaths.AbstractJcInstResult.JcFlowGraphResultImpl
import org.jacodb.impl.features.classpaths.AbstractJcInstResult.JcInstListResultImpl
import org.jacodb.impl.features.classpaths.AbstractJcInstResult.JcRawInstListResultImpl
import java.text.NumberFormat

/**
 * any class cache should extend this class
 */
open class ClasspathCache(settings: JcCacheSettings) : JcClasspathExtFeature, JcMethodExtFeature, KLogging() {

    private val cacheProvider = PluggableCacheProvider.getProvider(settings.cacheSpiId)

    private val classesCache = newSegment<String, JcResolvedClassResult>(settings.classes)

    private val typesCache = newSegment<TypeKey, JcResolvedTypeResult>(settings.types)

    private val rawInstCache = newSegment<JcMethod, JcInstList<JcRawInst>>(settings.rawInstLists)

    private val instCache = newSegment<JcMethod, JcInstList<JcInst>>(settings.instLists)

    private val cfgCache = newSegment<JcMethod, JcGraph>(settings.flowGraphs)

    private var javaObjectResolvedClass: JcResolvedClassResult? = null
    private var javaObjectResolvedType: JcResolvedTypeResult? = null
    private var javaObjectResolvedNotNullType: JcResolvedTypeResult? = null
    private var javaObjectResolvedNullableType: JcResolvedTypeResult? = null

    override fun tryFindClass(classpath: JcClasspath, name: String): JcResolvedClassResult? {
        return if (name == JAVA_OBJECT) javaObjectResolvedClass else classesCache[name]
    }

    override fun tryFindType(classpath: JcClasspath, name: String, nullable: Boolean?): JcResolvedTypeResult? {
        if (name == JAVA_OBJECT) {
            return when (nullable) {
                null -> javaObjectResolvedType
                true -> javaObjectResolvedNullableType
                false -> javaObjectResolvedNotNullType
            }
        }
        return typesCache[TypeKey(name, nullable)]
    }

    override fun flowGraph(method: JcMethod) = cfgCache[method]?.let {
        JcFlowGraphResultImpl(method, it)
    }

    override fun instList(method: JcMethod) = instCache[method]?.let {
        JcInstListResultImpl(method, it)
    }

    override fun rawInstList(method: JcMethod) = rawInstCache[method]?.let {
        JcRawInstListResultImpl(method, it)
    }

    override fun on(event: JcFeatureEvent) {
        when (val result = event.result) {
            is JcResolvedClassResult -> {
                val name = result.name
                if (name == JAVA_OBJECT) {
                    javaObjectResolvedClass = result
                } else {
                    classesCache[name] = result
                }
            }

            is JcResolvedTypeResult -> {
                val found = result.type
                if (found != null && found is JcClassType) {
                    val nullable = found.nullable
                    val typeName = result.name
                    if (typeName == JAVA_OBJECT) {
                        when (nullable) {
                            null -> javaObjectResolvedType = result
                            true -> javaObjectResolvedNullableType = result
                            false -> javaObjectResolvedNotNullType = result
                        }
                    } else {
                        typesCache[TypeKey(typeName, nullable)] = result
                    }
                }
            }

            is JcFlowGraphResult -> cfgCache[result.method] = result.flowGraph
            is JcInstListResult -> instCache[result.method] = result.instList
            is JcRawInstListResult -> rawInstCache[result.method] = result.rawInstList
        }
    }

    open fun stats(): Map<String, PluggableCacheStats> = buildMap {
        this["classes"] = classesCache.getStats()
        this["types"] = typesCache.getStats()
        this["cfg"] = cfgCache.getStats()
        this["raw-instructions"] = rawInstCache.getStats()
        this["instructions"] = instCache.getStats()
    }

    open fun dumpStats() {
        stats().entries.toList()
            .sortedBy { it.key }
            .forEach { (key, stat) ->
                logger.info(
                    "$key cache hit rate: ${
                        stat.hitRate.forPercentages()
                    }, total count ${stat.requestCount}"
                )
            }
    }

    private fun <K : Any, V : Any> newSegment(settings: JcCacheSegmentSettings): PluggableCache<K, V> {
        with(settings) {
            return cacheProvider.newCache {
                maximumSize = maxSize.toInt()
                expirationDuration = expiration
                valueRefType = valueStoreType
            }
        }
    }

    private fun Double.forPercentages(): String {
        return NumberFormat.getPercentInstance().format(this)
    }

    private data class TypeKey(val typeName: String, val nullable: Boolean? = null)
}
