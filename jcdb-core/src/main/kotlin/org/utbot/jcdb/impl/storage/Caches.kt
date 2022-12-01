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

package org.utbot.jcdb.impl.storage

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.time.Duration


fun <KEY, VALUE> cacheOf(size: Long): Cache<KEY, VALUE> {
    return CacheBuilder.newBuilder().maximumSize(size).expireAfterAccess(Duration.ofSeconds(10)).build()
}