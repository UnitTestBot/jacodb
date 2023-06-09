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

package org.jacodb.analysis.points2

import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.Points2Engine
import org.jacodb.analysis.points2.AllOverridesDevirtualizer.Companion.bannedPackagePrefixes
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.isSubClassOf
import org.jacodb.impl.features.hierarchyExt

/**
 * Simple devirtualizer that substitutes method with all of its overrides, but no more then [limit].
 * Also, it doesn't devirtualize methods matching [bannedPackagePrefixes]
 */
class AllOverridesDevirtualizer(
    private val initialGraph: JcApplicationGraph,
    private val classpath: JcClasspath,
    private val limit: Int? = null
) : Points2Engine, Devirtualizer {
    private val hierarchyExtension = runBlocking {
        classpath.hierarchyExt()
    }

    override fun findPossibleCallees(sink: JcInst): Collection<JcMethod> {
        val methods = initialGraph.callees(sink).toList()
        val callExpr = sink.callExpr as? JcVirtualCallExpr ?: return methods
        val instanceClass = (callExpr.instance.type as? JcClassType)?.jcClass ?: return methods

        return methods
            .flatMap { method ->
                if (bannedPackagePrefixes.any { method.enclosingClass.name.startsWith(it) })
                    listOf(method)
                else {
                    val allOverrides = hierarchyExtension
                        .findOverrides(method)
                        .filter {
                            it.enclosingClass isSubClassOf instanceClass ||
                            // TODO: use only down-most override here
                            instanceClass isSubClassOf it.enclosingClass
                        }

                    // TODO: maybe filter inaccessible methods here?
                    return if (limit != null) {
                        allOverrides.take(limit).toList() + listOf(method)
                    } else {
                        allOverrides.toList() + listOf(method)
                    }
                }
            }
    }

    companion object {
        private val bannedPackagePrefixes = listOf(
            "sun.",
            "jdk.internal.",
            "java.",
            "kotlin."
        )
    }

    override fun obtainDevirtualizer(): Devirtualizer {
        return this
    }
}