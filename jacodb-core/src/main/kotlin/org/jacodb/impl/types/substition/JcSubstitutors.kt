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

package org.jacodb.impl.types.substition

import org.jacodb.api.*
import org.jacodb.impl.cfg.util.OBJECT_CLASS
import org.jacodb.impl.types.signature.JvmClassRefType
import org.jacodb.impl.types.typeParameters

private fun List<JvmTypeParameterDeclaration>.substitute(parameters: List<JvmType>, outer: JcSubstitutor?): JcSubstitutor {
    val substitution = mapIndexed { index, declaration ->
        declaration to parameters[index]
    }.toMap()
    return (outer ?: JcSubstitutorImpl.empty).newScope(substitution)
}


object SafeSubstitution : JcGenericsSubstitutionFeature {

    override fun substitute(
        clazz: JcClassOrInterface,
        parameters: List<JvmType>,
        outer: JcSubstitutor?
    ): JcSubstitutor {
        val params = clazz.typeParameters
        require(params.size == parameters.size) {
            "Incorrect parameters specified for class ${clazz.name}: expected ${params.size} found ${parameters.size}"
        }
        return params.substitute(parameters, outer)
    }
}

object IgnoreProblemsSubstitution : JcGenericsSubstitutionFeature {

    private val jvmObjectType =  JvmClassRefType(OBJECT_CLASS, true, emptyList())

    override fun substitute(
        clazz: JcClassOrInterface,
        parameters: List<JvmType>,
        outer: JcSubstitutor?
    ): JcSubstitutor {
        val params = clazz.typeParameters
        if (params.size == parameters.size) {
            return params.substitute(parameters, outer)
        }
        val substitution = params.mapIndexed { index, declaration ->
            declaration to (declaration.bounds?.first() ?: jvmObjectType)
        }.toMap()
        return (outer ?: JcSubstitutorImpl.empty).newScope(substitution)
    }
}

