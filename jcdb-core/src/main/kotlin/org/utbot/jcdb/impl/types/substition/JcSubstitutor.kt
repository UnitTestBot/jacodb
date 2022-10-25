package org.utbot.jcdb.impl.types.substition

import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.impl.types.signature.JvmType
import org.utbot.jcdb.impl.types.signature.JvmTypeParameterDeclaration
import org.utbot.jcdb.impl.types.typeParameters

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

fun JcClassOrInterface.substitute(parameters: List<JvmType>, outer: JcSubstitutor?): JcSubstitutor {
    val params = typeParameters
    require(params.size == parameters.size) {
        "Incorrect parameters specified for class $name: expected ${params.size} found ${parameters.size}"
    }
    val substitution = params.mapIndexed { index, declaration ->
        declaration to parameters[index]
    }.toMap()
    return (outer ?: JcSubstitutor.empty).newScope(substitution)
}