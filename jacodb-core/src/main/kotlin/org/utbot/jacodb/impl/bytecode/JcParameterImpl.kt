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

package org.utbot.jacodb.impl.bytecode

import org.utbot.jacodb.api.JcAnnotation
import org.utbot.jacodb.api.JcDeclaration
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.JcParameter
import org.utbot.jacodb.api.TypeName
import org.utbot.jacodb.impl.types.ParameterInfo
import org.utbot.jacodb.impl.types.TypeNameImpl

class JcParameterImpl(
    override val method: JcMethod,
    private val info: ParameterInfo
) : JcParameter {

    override val access: Int
        get() = info.access

    override val name: String? by lazy {
        info.name ?: kmParameter?.name
    }

    override val index: Int
        get() = info.index

    override val declaration: JcDeclaration
        get() = JcDeclarationImpl.of(method.enclosingClass.declaration.location, this)

    override val annotations: List<JcAnnotation>
        get() = info.annotations.map { JcAnnotationImpl(it, method.enclosingClass.classpath) }

    override val type: TypeName
        get() = TypeNameImpl(info.type)

}