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
import org.jacodb.api.JcClassFoundEvent
import org.jacodb.api.JcClassNotFound
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathExtFeature
import org.jacodb.api.JcClasspathFeatureEvent
import org.jacodb.api.JcInstExtFeature
import org.jacodb.api.JcMethod
import org.jacodb.api.JcMethodExtFeature
import org.jacodb.api.JcMethodRef
import org.jacodb.api.JcType
import org.jacodb.api.JcTypeFoundEvent
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.impl.JcCacheSettings
import org.jacodb.impl.cfg.JcGraphBuilder
import org.jacodb.impl.cfg.JcMethodRefImpl
import org.jacodb.impl.cfg.RawInstListBuilder
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

    private val graphCache = segmentBuilder(settings.graphs)
        .build(object : CacheLoader<JcMethodRef, JcGraphHolder>() {
            override fun load(key: JcMethodRef): JcGraphHolder {
                return JcGraphHolder(key)
            }
        });


    override fun tryFindClass(classpath: JcClasspath, name: String): Optional<JcClassOrInterface>? {
        return classesCache.getIfPresent(name)
    }

    override fun tryFindType(classpath: JcClasspath, name: String): Optional<JcType>? {
        return typesCache.getIfPresent(name)
    }

    override fun flowGraph(method: JcMethod): JcGraph = method.holder().flowGraph

    private fun JcMethod.holder(): JcGraphHolder {
        return graphCache.getUnchecked(JcMethodRefImpl(this)).also {
            it.bind(enclosingClass.classpath)
        }
    }

    override fun instList(method: JcMethod): JcInstList<JcInst> = method.holder().instList

    override fun rawInstList(method: JcMethod): JcInstList<JcRawInst> =
        method.holder().rawInstList


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

    protected fun segmentBuilder(settings: Pair<Long, Duration>): CacheBuilder<Any, Any> {
        val maxSize = settings.first
        val expiration = settings.second

        return CacheBuilder.newBuilder()
            .expireAfterAccess(expiration)
            .softValues()
            .maximumSize(maxSize)
    }
}

private class JcGraphHolder(private val methodRef: JcMethodRef) {

    private val method get() = methodRef.method
    private lateinit var classpath: JcClasspath
    private lateinit var methodFeatures: List<JcInstExtFeature>

    fun bind(classpath: JcClasspath) {
        this.classpath = classpath
        this.methodFeatures = classpath.features?.filterIsInstance<JcInstExtFeature>().orEmpty()
    }

    val rawInstList: JcInstList<JcRawInst> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val list: JcInstList<JcRawInst> = RawInstListBuilder(method, method.asmNode()).build()
        methodFeatures.fold(list) { value, feature ->
            feature.transformRawInstList(method, value)
        }
    }

    val flowGraph by lazy(LazyThreadSafetyMode.PUBLICATION) {
        JcGraphBuilder(method, rawInstList).buildFlowGraph()
    }

    val instList: JcInstList<JcInst> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val list: JcInstList<JcInst> = JcGraphBuilder(method, rawInstList).buildInstList()
        methodFeatures.fold(list) { value, feature ->
            feature.transformInstList(method, value)
        }
    }

}
