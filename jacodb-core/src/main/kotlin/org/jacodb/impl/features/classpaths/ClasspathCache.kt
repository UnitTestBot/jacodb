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
import com.google.common.cache.CacheLoader
import com.google.common.cache.CacheStats
import org.jacodb.api.JcClassFoundEvent
import org.jacodb.api.JcClassNotFound
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathExtFeature
import org.jacodb.api.JcClasspathFeatureEvent
import org.jacodb.api.JcMethod
import org.jacodb.api.JcMethodExtFeature
import org.jacodb.api.JcType
import org.jacodb.api.JcTypeFoundEvent
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.impl.JcCacheSettings
import org.jacodb.impl.cfg.nonCachedFlowGraph
import org.jacodb.impl.cfg.nonCachedInstList
import org.jacodb.impl.cfg.nonCachedRawInstList
import java.time.Duration
import java.util.*


/**
 * any class cache should extend this class
 */
open class ClasspathCache(settings: JcCacheSettings) : JcClasspathExtFeature, JcMethodExtFeature {
    /**
     *
     */
    private val classesCache = segmentBuilder(settings.classes)
        .build<String, Optional<JcClassOrInterface>>()

    private val typesCache = segmentBuilder(settings.types)
        .build<String, Optional<JcType>>()

    private val rawInstCache = segmentBuilder(settings.graphs)
        .build(object : CacheLoader<JcMethod, JcInstList<JcRawInst>>() {
            override fun load(key: JcMethod): JcInstList<JcRawInst> {
                return nonCachedRawInstList(key)
            }
        });

    private val instCache = segmentBuilder(settings.graphs, weakValues = true)
        .build(object : CacheLoader<JcMethod, JcInstList<JcInst>>() {
            override fun load(key: JcMethod): JcInstList<JcInst> {
                return nonCachedInstList(key)
            }
        });

    private val cfgCache = segmentBuilder(settings.graphs, weakValues = true)
        .build(object : CacheLoader<JcMethod, JcGraph>() {
            override fun load(key: JcMethod): JcGraph {
                return nonCachedFlowGraph(key)
            }
        });


    override fun tryFindClass(classpath: JcClasspath, name: String): Optional<JcClassOrInterface>? {
        return classesCache.getIfPresent(name)
    }

    override fun tryFindType(classpath: JcClasspath, name: String): Optional<JcType>? {
        return typesCache.getIfPresent(name)
    }

    override fun flowGraph(method: JcMethod) = cfgCache.getUnchecked(method)
    override fun instList(method: JcMethod) = instCache.getUnchecked(method)
    override fun rawInstList(method: JcMethod) = rawInstCache.getUnchecked(method)

    override fun on(event: JcClasspathFeatureEvent) {
        when (event) {
            is JcClassFoundEvent -> classesCache.put(event.clazz.name, Optional.of(event.clazz))
            is JcClassNotFound -> classesCache.put(event.name, Optional.empty())
            is JcTypeFoundEvent -> {
                val type = event.type
                if (type is JcClassType && type.typeParameters.isEmpty()) {
                    typesCache.put(event.type.typeName, Optional.of(event.type))
                }
            }
        }
    }

    protected fun segmentBuilder(settings: Pair<Long, Duration>, weakValues: Boolean = false): CacheBuilder<Any, Any> {
        val maxSize = settings.first
        val expiration = settings.second

        return CacheBuilder.newBuilder()
            .expireAfterAccess(expiration)
            .recordStats()
            .maximumSize(maxSize).let {
                if (weakValues) {
                    it.weakValues()
                } else {
                    it.softValues()
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
}