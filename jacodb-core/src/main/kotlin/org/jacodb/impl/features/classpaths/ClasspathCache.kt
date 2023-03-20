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
import org.jacodb.api.JcClassFoundEvent
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathExtFeature
import org.jacodb.api.JcClasspathFeatureEvent
import org.jacodb.api.JcType
import org.jacodb.api.JcTypeFoundEvent
import java.time.Duration


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
        .maximumSize(maxSize)
        .build<String, JcClassOrInterface>()

    /**
     *
     */
    private val typesCache = CacheBuilder.newBuilder()
        .expireAfterAccess(expiration)
        .maximumSize(maxSize)
        .build<String, JcType>()


    override fun tryFindClass(classpath: JcClasspath, name: String): JcClassOrInterface? {
        return classesCache.getIfPresent(name)
    }

    override fun on(event: JcClasspathFeatureEvent) {
        when(event) {
            is JcClassFoundEvent -> classesCache.put(event.clazz.name, event.clazz)
            is JcTypeFoundEvent -> typesCache.put(event.type.typeName, event.type)
        }
    }
}