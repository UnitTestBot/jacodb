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

import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.findClass
import org.jacodb.impl.types.signature.JvmType
import org.jacodb.impl.types.signature.JvmTypeParameterDeclaration
import org.jacodb.impl.types.typeParameters

interface JcSubstitutor {

    companion object {
        val empty = JcSubstitutorImpl()
    }

    /**
     * Returns a mapping that this substitutor contains for a given type parameter.
     * Does not perform bounds promotion
     *
     * @param typeParameter the parameter to return the mapping for.
     * @return the mapping for the type parameter, or `null` for a raw type.
     */
    fun substitution(typeParameter: JvmTypeParameterDeclaration): JvmType?

    /**
     * Substitutes type parameters occurring in `type` with their values.
     * If value for type parameter is `null`, appropriate erasure is returned.
     *
     * @param type the type to substitute the type parameters for.
     * @return the result of the substitution.
     */
    fun substitute(type: JvmType): JvmType

    fun fork(explicit: Map<JvmTypeParameterDeclaration, JvmType>): JcSubstitutor

    fun newScope(declarations: List<JvmTypeParameterDeclaration>): JcSubstitutor

    fun newScope(explicit: Map<JvmTypeParameterDeclaration, JvmType>): JcSubstitutor

    val substitutions: Map<JvmTypeParameterDeclaration, JvmType>

}

fun JcClasspath.substitute(name: String, parameters: List<JvmType>, outer: JcSubstitutor?): JcSubstitutor {
    val clazz = findClass(name)
    val params = clazz.typeParameters
    require(params.size == parameters.size) {
        "Incorrect parameters specified for class $name: expected ${params.size} found ${parameters.size}"
    }
    val substitution = params.mapIndexed { index, declaration ->
        declaration to parameters[index]
    }.toMap()
    return (outer ?: JcSubstitutor.empty).newScope(substitution)
}