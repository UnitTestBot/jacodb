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
import org.jacodb.api.JcInstExtFeature
import org.jacodb.api.JcMethod
import org.jacodb.api.JcMethodRef
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.impl.bytecode.jsrInlined
import org.jacodb.impl.cfg.JcGraphBuilder
import org.jacodb.impl.cfg.JcMethodRefImpl
import org.jacodb.impl.cfg.RawInstListBuilder
import java.time.Duration
import kotlin.LazyThreadSafetyMode.PUBLICATION

open class GraphsService(
    private val methodFeatures: List<JcInstExtFeature>?,
    protected val maxSize: Long = 10_000,
    protected val expiration: Duration = Duration.ofMinutes(1)
) {

    private val graphCache = CacheBuilder.newBuilder()
        .expireAfterAccess(expiration)
        .softValues()
        .maximumSize(maxSize)
        .build(object : CacheLoader<JcMethodRef, JcGraphHolder>() {
            override fun load(key: JcMethodRef): JcGraphHolder {
                return JcGraphHolder(key, methodFeatures)
            }
        });


    fun flowGraph(method: JcMethod): JcGraph = graphCache.getUnchecked(JcMethodRefImpl(method)).flowGraph

    fun instList(method: JcMethod): JcInstList<JcInst> = graphCache.getUnchecked(JcMethodRefImpl(method)).instList

    fun rawInstList(method: JcMethod): JcInstList<JcRawInst> =
        graphCache.getUnchecked(JcMethodRefImpl(method)).rawInstList

}

private class JcGraphHolder(private val methodRef: JcMethodRef, private val methodFeatures: List<JcInstExtFeature>?) {

    private val method get() = methodRef.method

    val rawInstList: JcInstList<JcRawInst> by lazy(PUBLICATION) {
        val list: JcInstList<JcRawInst> = RawInstListBuilder(method, method.asmNode().jsrInlined).build()
        methodFeatures?.fold(list) { value, feature ->
            feature.transformRawInstList(method, value)
        } ?: list
    }

    val flowGraph by lazy(PUBLICATION) {
        JcGraphBuilder(method, rawInstList).buildFlowGraph()
    }

    val instList: JcInstList<JcInst> by lazy(PUBLICATION) {
        val list: JcInstList<JcInst> = JcGraphBuilder(method, rawInstList).buildInstList()
        methodFeatures?.fold(list) { value, feature ->
            feature.transformInstList(method, value)
        } ?: list
    }

}
