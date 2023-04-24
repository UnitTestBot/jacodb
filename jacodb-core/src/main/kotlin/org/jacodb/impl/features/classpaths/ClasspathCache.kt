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
import org.jacodb.api.*
import java.time.Duration
import java.util.*


/**
 * any class cache should extend this class
 */
open class ClasspathCache(
    protected val maxSize: Long = 10_000,
    protected val expiration: Duration = Duration.ofMinutes(1)
) : JcClasspathExtFeature {
    /**
     *
     */
    private val classesCache = CacheBuilder.newBuilder()
        .expireAfterAccess(expiration)
        .softValues()
        .maximumSize(maxSize)
        .build<String, Optional<JcClassOrInterface>>()
    /**
     *
     */
    private val typesCache = CacheBuilder.newBuilder()
        .expireAfterAccess(expiration)
        .softValues()
        .maximumSize(maxSize)
        .build<String, Optional<JcType>>()

    override fun tryFindClass(classpath: JcClasspath, name: String): Optional<JcClassOrInterface>? {
        return classesCache.getIfPresent(name)
    }

    override fun tryFindType(classpath: JcClasspath, name: String): Optional<JcType>? {
        return typesCache.getIfPresent(name)
    }

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
}