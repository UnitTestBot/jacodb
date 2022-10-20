package org.utbot.jcdb.impl.types.substition

import kotlinx.collections.immutable.toPersistentMap
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

    val substitutions: Map<JvmTypeParameterDeclaration, JvmType>

}

fun JcClassOrInterface.substitute(parameters: List<JvmType>): JcSubstitutor {
    val params = typeParameters
    require(params.size == parameters.size) {
        "Incorrect parameters specified for class $name: expected ${params.size} found ${parameters.size}"
    }
    return JcSubstitutorImpl(
        params.mapIndexed { index, declaration -> declaration to parameters[index] }
            .toMap().toPersistentMap()
    )
}

private suspend fun composeSubstitutors(
    outer: JcSubstitutor,
    inner: JcSubstitutor,
    onClass: JcClassOrInterface
): JcSubstitutor {
//    var answer: JcSubstitutor = JcSubstitutor.empty
//    val outerMap = outer.substitutions
//    val innerMap = inner.substitutions
//    for (parameter in onClass.typeParameters()) {
//        if (outerMap.containsKey(parameter) || innerMap.containsKey(parameter)) {
//            val innerType = inner.substitute(parameter)!!
//            val paramCandidate =
//                innerType as? JcClassType //if (PsiCapturedWildcardType.isCapture()) (innerType as? JcClassType)?.jcClass else null
//            var targetType: JcType?
//            if (paramCandidate != null && paramCandidate !== parameter) {
//                targetType = outer.substitute(paramCandidate)
//            } else {
//                targetType = outer.substitute(innerType)
//            }
//            answer = answer.put(parameter, targetType)
//        }
//    }
//    return answer
    TODO()
}