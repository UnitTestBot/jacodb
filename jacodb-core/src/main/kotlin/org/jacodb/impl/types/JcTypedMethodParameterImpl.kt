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

package org.jacodb.impl.types

import org.jacodb.api.*
import org.jacodb.api.ext.isNullable
import org.jacodb.impl.bytecode.JcAnnotationImpl
import org.jacodb.impl.bytecode.JcMethodImpl

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
            } ?: classpath.findTypeOrNull(typeName)
                ?.copyWithAnnotations(
                    (enclosingMethod.method as? JcMethodImpl)?.parameterTypeAnnotationInfos(parameter.index)?.map { JcAnnotationImpl(it, classpath) } ?: listOf()
                ) ?: typeName.throwClassNotFound()

            return parameter.isNullable?.let {
                (type as? JcRefType)?.copyWithNullability(it)
            } ?: type
        }

    override val name: String?
        get() = parameter.name
}