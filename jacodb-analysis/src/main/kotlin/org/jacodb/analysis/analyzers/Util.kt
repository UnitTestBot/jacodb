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

package org.jacodb.analysis.analyzers

import org.jacodb.analysis.paths.AccessPath
import org.jacodb.analysis.paths.minus
import org.jacodb.analysis.paths.startsWith
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.ext.toType

val JcMethod.thisInstance: JcThis
    get() = JcThis(enclosingClass.toType())

fun normalFactFlow(fact: TaintNode, fromPath: AccessPath, toPath: AccessPath, dropFact: Boolean, maxPathLength: Int): List<TaintNode> {
    val factPath = fact.variable
    val default = if (dropFact) emptyList() else listOf(fact)

    // Second clause is important here as it saves from false positive aliases, see
    //  #AnalysisTest.`dereferencing copy of value saved before null assignment produce no npe`
    val diff = factPath.minus(fromPath)
    if (diff != null && (fact.activation == null || fromPath != factPath)) {
        return default
            .plus(fact.moveToOtherPath(AccessPath.fromOther(toPath, diff).limit(maxPathLength)))
            .distinct()
    }

    if (factPath.startsWith(toPath)) {
        return emptyList()
    }

    return default
}