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

package org.jacodb.approximation

import org.jacodb.api.jvm.TypeName
import org.jacodb.approximation.Approximations.findOriginalByApproximationOrNull
import org.jacodb.impl.cfg.util.asArray
import org.jacodb.impl.cfg.util.baseElementType
import org.jacodb.impl.cfg.util.isArray
import org.jacodb.impl.types.TypeNameImpl

fun String.toApproximationName() = ApproximationClassName(this)
fun String.toOriginalName() = OriginalClassName(this)

fun TypeName.eliminateApproximation(): TypeName {
    if (this.isArray) {
        val (elemType, dim) = this.baseElementType()
        val resultElemType = elemType.eliminateApproximation()
        return resultElemType.asArray(dim)
    }
    val originalClassName = findOriginalByApproximationOrNull(typeName.toApproximationName()) ?: return this
    return TypeNameImpl(originalClassName)
}
