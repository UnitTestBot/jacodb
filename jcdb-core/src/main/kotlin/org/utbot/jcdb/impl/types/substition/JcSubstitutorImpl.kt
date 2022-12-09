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

package org.utbot.jcdb.impl.types.substition

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import org.utbot.jcdb.impl.types.signature.JvmType
import org.utbot.jcdb.impl.types.signature.JvmTypeParameterDeclaration
import org.utbot.jcdb.impl.types.signature.JvmTypeParameterDeclarationImpl
import org.utbot.jcdb.impl.types.signature.JvmTypeVariable
import org.utbot.jcdb.impl.types.signature.copyWithNullability

class JcSubstitutorImpl(
    // map declaration -> actual type or type variable
    override val substitutions: PersistentMap<JvmTypeParameterDeclaration, JvmType> = persistentMapOf()
) : JcSubstitutor {

    private val substitutionTypeVisitor = object : JvmTypeVisitor {

        override fun visitUnprocessedTypeVariable(type: JvmTypeVariable, context: VisitorContext): JvmType {
            val direct = substitutions.firstNotNullOfOrNull { if (it.key.symbol == type.symbol) it.value else null }
            if (direct != null) {
                // Substituted type is nullable iff type variable is nullable (declared as T?) or substituting type is nullable
                return direct.copyWithNullability(direct.isNullable || type.isNullable)
            }
            return type.declaration?.let {
                JvmTypeVariable(visitDeclaration(it, context), type.isNullable)
            } ?: type
        }
    }

    override fun substitution(typeParameter: JvmTypeParameterDeclaration): JvmType? {
        return substitutions[typeParameter]
    }

    override fun substitute(type: JvmType): JvmType {
        return substitutionTypeVisitor.visitType(type)
    }

    override fun newScope(declarations: List<JvmTypeParameterDeclaration>): JcSubstitutor {
        if (declarations.isEmpty()) {
            return this
        }
        val incomingSymbols = declarations.map { it.symbol }.toSet() // incoming symbols may override current
        val filtered = substitutions.filterNot { incomingSymbols.contains(it.key.symbol) }
        return JcSubstitutorImpl(
            (filtered + declarations.associateWith {
                JvmTypeVariable(substitute(it, incomingSymbols))
            }).toPersistentMap()
        )
    }

    override fun newScope(explicit: Map<JvmTypeParameterDeclaration, JvmType>): JcSubstitutor {
        if (explicit.isEmpty()) {
            return this
        }
        val incomingSymbols = explicit.keys.map { it.symbol }.toHashSet() // incoming symbols may override current
        val filtered = substitutions.filterNot { incomingSymbols.contains(it.key.symbol) }
        return JcSubstitutorImpl((filtered + explicit).toPersistentMap())
    }

    override fun fork(explicit: Map<JvmTypeParameterDeclaration, JvmType>): JcSubstitutor {
        val incomingSymbols = explicit.keys.map { it.symbol }.toSet() // incoming symbols may override current
        val forked = explicit.map {
            substitute(it.key, incomingSymbols) to substitute(it.value)
        }.toMap().toPersistentMap()
        return JcSubstitutorImpl(forked)
    }

    private fun substitute(
        declaration: JvmTypeParameterDeclaration,
        ignoredSymbols: Set<String>
    ): JvmTypeParameterDeclaration {
        val visitor = object : JvmTypeVisitor {

            override fun visitUnprocessedTypeVariable(type: JvmTypeVariable, context: VisitorContext): JvmType {
                if (ignoredSymbols.contains(type.symbol)) {
                    return type
                }
                return substitutions.firstNotNullOfOrNull { if (it.key.symbol == type.symbol) it.value else null }?.let {
                    // Substituted type is nullable iff type variable is nullable (declared as T?) or substituting type is nullable
                    it.copyWithNullability(it.isNullable || type.isNullable)
                } ?: type
            }
        }
        return JvmTypeParameterDeclarationImpl(
            declaration.symbol,
            declaration.owner,
            declaration.bounds?.map { visitor.visitType(it) })
    }

}
