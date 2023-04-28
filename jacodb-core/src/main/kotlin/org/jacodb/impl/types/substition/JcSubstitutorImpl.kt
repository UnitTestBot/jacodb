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

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import org.jacodb.impl.types.signature.JvmType
import org.jacodb.impl.types.signature.JvmTypeParameterDeclaration
import org.jacodb.impl.types.signature.JvmTypeParameterDeclarationImpl
import org.jacodb.impl.types.signature.JvmTypeVariable
import org.jacodb.impl.types.signature.copyWithNullability

class JcSubstitutorImpl(
    // map declaration -> actual type or type variable
    override val substitutions: PersistentMap<JvmTypeParameterDeclaration, JvmType> = persistentMapOf()
) : JcSubstitutor {

    private val substitutionTypeVisitor = object : JvmTypeVisitor {

        override fun visitUnprocessedTypeVariable(type: JvmTypeVariable, context: VisitorContext): JvmType {
            val direct = substitutions.firstNotNullOfOrNull { if (it.key.symbol == type.symbol) it.value else null }
            if (direct != null) {
                return relaxNullabilityAfterSubstitution(type, direct)
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
                // TODO: nullability=false is a hack here: there is no TypeVariable at this moment
                //  so we need "neutral" element, such that its substitution by other type would return the latter
                JvmTypeVariable(substitute(it, incomingSymbols), false)
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
                return substitutions.firstNotNullOfOrNull { if (it.key.symbol == type.symbol) it.value else null }
                    ?.let {
                        relaxNullabilityAfterSubstitution(type, it)
                    } ?: type
            }
        }
        return JvmTypeParameterDeclarationImpl(
            declaration.symbol,
            declaration.owner,
            declaration.bounds?.map { visitor.visitType(it) })
    }

    /**
     * The table below represents how nullability is maintained during substitutions.
     * Each column stands for type of type variable (`kt` or `java` prefix denotes language in which it was declared).
     * In the same manner, each raw stands for type with which type variable was substituted.
     * Cells in the intersection denote resulting type with its nullability (written in Kotlin notation):
     *
     *
     * | Substituion\TypeVariable | kt T | kt T? | java @NotNull T | java @Nullable T | java T |
     * |--------------------------|------|-------|-----------------|------------------|--------|
     * | kt R & java @NotNull R   | R    | R?    | R               | R?               | R!     |
     * | kt R? & java @Nullable R | R?   | R?    | R               | R?               | R?     |
     * | java R (== kt R!)        | R!   | R?    | R               | R?               | R!     |
     *
     *
     * Here R can be type variable as well as concrete type as well as any other reference type
     * (so you can substitute R with, e.g., String everywhere in the table, and it will still be right).
     *
     * This data was obtained empirically, and you may check it by looking at derived types for fields/properties of
     * `NullAnnotationExamples.ktContainerOfUndefined`, `KotlinNullabilityExamples.javaContainerOfNullable`, etc.
     */
    private fun relaxNullabilityAfterSubstitution(typeVar: JvmTypeVariable, type: JvmType): JvmType {
        val typeVarNullability = typeVar.isNullable
        val substNullability = type.isNullable
        // TODO: Java's `@NotNull T` and Kotlin `T` behave differently (see the table), treat the first one correctly
        return when {
            // T? and Java @Nullable T will always produce nullable type
            typeVarNullability == true -> type.copyWithNullability(true)

            // We don't know nullability of Java's T unless it is replaced with nullable type (in which case it is nullable)
            typeVarNullability == null && substNullability == true -> type
            typeVarNullability == null && substNullability != true -> type.copyWithNullability(null)

            // Kotlin's default T doesn't change nullability
            else -> type
        }
    }

}
