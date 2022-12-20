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

package org.utbot.jacodb.impl.fs

import org.utbot.jacodb.api.ClassSource
import org.utbot.jacodb.api.RegisteredLocation

val RegisteredLocation.sources: List<ClassSource>
    get() {
        return jcLocation?.classes?.map {
            ClassSourceImpl(this, it.key, it.value)
        }.orEmpty()
    }

val RegisteredLocation.lazySources: List<ClassSource>
    get() {
        val classNames = jcLocation?.classNames ?: return emptyList()
        if (classNames.any { it.startsWith("java.") }) {
            return sources
        }
        return classNames.map {
            LazyClassSourceImpl(this, it)
        }
    }