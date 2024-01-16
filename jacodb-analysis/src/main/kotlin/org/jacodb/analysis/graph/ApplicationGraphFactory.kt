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

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.jacodb.api.jvm.JcProject
import org.jacodb.api.jvm.analysis.JcApplicationGraph
import org.jacodb.impl.features.usagesExt
import java.util.concurrent.CompletableFuture

/**
 * Creates an instance of [SimplifiedJcApplicationGraph], see its docs for more info.
 */
suspend fun JcProject.newApplicationGraphForAnalysis(bannedPackagePrefixes: List<String>? = null): JcApplicationGraph {
    val mainGraph = JcApplicationGraphImpl(this, usagesExt())
    return if (bannedPackagePrefixes != null) {
        SimplifiedJcApplicationGraph(mainGraph, bannedPackagePrefixes)
    } else {
        SimplifiedJcApplicationGraph(mainGraph, defaultBannedPackagePrefixes)
    }
}

fun JcProject.asyncNewApplicationGraphForAnalysis(
    bannedPackagePrefixes: List<String>? = null
): CompletableFuture<JcApplicationGraph> {
    return GlobalScope.future {
        newApplicationGraphForAnalysis(bannedPackagePrefixes)
    }
}

val defaultBannedPackagePrefixes: List<String> = listOf(
    "kotlin.",
    "java.",
    "jdk.internal.",
    "sun.",
    "javax.",
)