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

package org.jacodb.analysis.engine

import org.jacodb.analysis.library.JcPackageUnitResolver
import org.jacodb.analysis.library.JcSingletonUnitResolver
import org.jacodb.analysis.library.getJcClassUnitResolver
import org.jacodb.analysis.library.methodUnitResolver
import org.jacodb.analysis.runAnalysis
import org.jacodb.api.jvm.JcMethod

/**
 * Sets a mapping from a [Method] to abstract domain [UnitType].
 *
 * Therefore, it splits all methods into units, containing one or more method each
 * (unit is a set of methods with same value of [UnitType] returned by [resolve]).
 *
 * To get more info about how it is used in analysis, see [runAnalysis].
 */
fun interface UnitResolver<UnitType, Method> {
    fun resolve(method: Method): UnitType

    companion object {
        fun getJcResolverByName(name: String): UnitResolver<*, JcMethod> { // TODO can we use an asterisk here?
            return when (name) {
                "method"    -> methodUnitResolver()
                "class"     -> getJcClassUnitResolver(false)
                "package"   -> JcPackageUnitResolver
                "singleton" -> JcSingletonUnitResolver
                else        -> error("Unknown unit resolver $name")
            }
        }
    }
}
