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

package org.jacodb.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

val BACKGROUND_PARALLELISM
    get() = Integer.getInteger(
        "org.jacodb.background.parallelism",
        64.coerceAtLeast(Runtime.getRuntime().availableProcessors())
    )

class BackgroundScope : CoroutineScope {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val coroutineContext = Dispatchers.IO.limitedParallelism(BACKGROUND_PARALLELISM) + SupervisorJob()
}

fun <W, T> softLazy(getter: () -> T): ReadOnlyProperty<W, T> {
    return object : ReadOnlyProperty<W, T> {
        @Volatile
        var softRef = SoftReference<T>(null)

        override fun getValue(thisRef: W, property: KProperty<*>): T {
            var instance = softRef.get()
            if (instance == null) {
                instance = getter()
                softRef = SoftReference(instance)
                return instance
            }
            return instance
        }
    }
}

fun <W, T> weakLazy(getter: () -> T): ReadOnlyProperty<W, T> {
    return object : ReadOnlyProperty<W, T> {
        @Volatile
        var weakRef = WeakReference<T>(null)

        override fun getValue(thisRef: W, property: KProperty<*>): T {
            var instance = weakRef.get()
            if (instance == null) {
                instance = getter()
                weakRef = WeakReference(instance)
                return instance
            }
            return instance
        }
    }
}