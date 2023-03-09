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

package org.jacodb.analysis.codegen.ast.impl

import org.jacodb.analysis.codegen.ast.base.TypeUsage

class ArrayTypeUsageImpl(override val element: TypeUsage, override val isNullable: Boolean) :
    TypeUsageImpl(), org.jacodb.analysis.codegen.ast.base.ArrayTypeUsage {

    override val stringPresentation: String = element.stringPresentation + "[]"

    override fun furthestElementType(): TypeUsage {
        if (element is org.jacodb.analysis.codegen.ast.base.ArrayTypeUsage)
            return element.furthestElementType()
        return element
    }

    override fun flipNullability(): TypeUsage {
        // todo flip inner element nullability?
        return ArrayTypeUsageImpl(element, !isNullable)
    }
}