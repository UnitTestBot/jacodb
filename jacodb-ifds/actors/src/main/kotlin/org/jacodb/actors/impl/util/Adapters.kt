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

package org.jacodb.actors.impl.util

import org.jacodb.actors.api.ActorPath
import org.jacodb.actors.api.ActorRef

//fun <T, R> ActorRef<T>.adaptWith(adapter: (R) -> T?): ActorRef<R> = object : ActorRef<R>{
//    override val path: ActorPath
//        get() = this@adaptWith.path
//
//    override suspend fun send(message: R) {
//        val adaptedMessage = adapter(message) ?: return
//        this@adaptWith.send(adaptedMessage)
//    }
//}
//
//inline fun <R, reified T : R> ActorRef<T>.adaptType(): ActorRef<R> = adaptWith { message ->
//    message as? T
//}
