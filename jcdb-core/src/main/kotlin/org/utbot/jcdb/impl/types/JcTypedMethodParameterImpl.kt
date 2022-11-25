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

package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcParameter
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.JcTypedMethodParameter
import org.utbot.jcdb.api.ext.kmType
import org.utbot.jcdb.api.ext.updateNullability
import org.utbot.jcdb.api.ext.isNullable
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.types.signature.JvmType
import org.utbot.jcdb.impl.types.substition.JcSubstitutor

class JcTypedMethodParameterImpl(
    override val enclosingMethod: JcTypedMethod,
    private val parameter: JcParameter,
    private val jvmType: JvmType?,
    private val substitutor: JcSubstitutor
) : JcTypedMethodParameter {

    val classpath = enclosingMethod.method.enclosingClass.classpath

    override val type: JcType
        get() {
            val typeName = parameter.type.typeName
            val type = jvmType?.let {
                classpath.typeOf(substitutor.substitute(jvmType))
            } ?: classpath.findTypeOrNull(typeName) ?: typeName.throwClassNotFound()

            return type.updateNullability(parameter.kmType, parameter.isNullable)
        }

    override val nullable: Boolean
        get() = parameter.isNullable //if (type != null && type.nullable) parameter.isNullable else false

    override val name: String?
        get() = parameter.name
}