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
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathExtFeature
import org.jacodb.api.JcFeatureEvent
import org.jacodb.api.JcMethod
import org.jacodb.api.JcMethodExtFeature
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.impl.JcCacheSegmentSettings
import org.jacodb.impl.JcCacheSettings
import org.jacodb.impl.ValueStoreType
import java.text.NumberFormat
import java.util.*


/**
 * any class cache should extend this class
 */
open class ClasspathCache(settings: JcCacheSettings) : JcClasspathExtFeature, JcMethodExtFeature {

    companion object : KLogging()

    private val classesCache = segmentBuilder(settings.classes)
        .build<String, Optional<JcClassOrInterface>>()

    private val typesCache = segmentBuilder(settings.types)
        .build<String, Optional<JcType>>()

    private val rawInstCache = segmentBuilder(settings.rawInstLists)
        .build<JcMethod, JcInstList<JcRawInst>>()

    private val instCache = segmentBuilder(settings.instLists)
        .build<JcMethod, JcInstList<JcInst>>()

    private val cfgCache = segmentBuilder(settings.flowGraphs)
        .build<JcMethod, JcGraph>()


    override fun tryFindClass(classpath: JcClasspath, name: String): Optional<JcClassOrInterface>? {
        return classesCache.getIfPresent(name)
    }

    override fun tryFindType(classpath: JcClasspath, name: String): Optional<JcType>? {
        return typesCache.getIfPresent(name)
    }

    override fun flowGraph(method: JcMethod) = cfgCache.getIfPresent(method)
    override fun instList(method: JcMethod) = instCache.getIfPresent(method)
    override fun rawInstList(method: JcMethod) = rawInstCache.getIfPresent(method)

    override fun on(event: JcFeatureEvent) {
        val result = event.result
        val input = event.input
        when (result) {
            is Optional<*> -> {
                if (result.isPresent) {
                    val found = result.get()
                    if (found is JcClassOrInterface) {
                        classesCache.put(found.name, Optional.of(found))
                    } else if (found is JcClassType && found.typeParameters.isEmpty()) {
                        typesCache.put(found.typeName, Optional.of(found))
                    }
                } else {
                    val name = input[0] as String
                    classesCache.put(name, Optional.empty())
                    typesCache.put(name, Optional.empty())
                }
            }

            is JcGraph -> {
                val method = input[0] as JcMethod
                cfgCache.put(method, result)
            }

            is JcInstList<*> -> {
                val method = input[0] as JcMethod
                if (result.instructions.isEmpty()) {
                    instCache.put(method, result as JcInstList<JcInst>)
                    rawInstCache.put(method, result as JcInstList<JcRawInst>)
                    return
                }
                if (result.instructions.first() is JcInst) {
                    instCache.put(method, result as JcInstList<JcInst>)
                } else {
                    rawInstCache.put(method, result as JcInstList<JcRawInst>)
                }
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