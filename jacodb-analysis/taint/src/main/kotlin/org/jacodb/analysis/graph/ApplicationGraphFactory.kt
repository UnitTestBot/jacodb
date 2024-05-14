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

@file:JvmName("ApplicationGraphFactory")

package org.jacodb.analysis.graph

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.jacodb.api.JcClasspath
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.impl.features.usagesExt
import java.util.concurrent.CompletableFuture

/**
 * Async adapter for calling [newApplicationGraphForAnalysis] from Java.
 *
 * See also: [answer on StackOverflow](https://stackoverflow.com/a/52887677/3592218).
 */
@OptIn(DelicateCoroutinesApi::class)
fun JcClasspath.newApplicationGraphForAnalysisAsync(): CompletableFuture<JcApplicationGraph> =
    GlobalScope.future {
        JcApplicationGraphImpl(this@newApplicationGraphForAnalysisAsync, usagesExt())
    }

val defaultBannedPackagePrefixes: List<String> = listOf(
    "kotlin.",
    "java.",
    "jdk.internal.",
    "sun.",
    "com.sun.",
    "javax.",
)
