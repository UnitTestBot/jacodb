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

import kotlinx.metadata.KmType
import org.utbot.jcdb.api.JcBoundedWildcard
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcTypeVariable
import org.utbot.jcdb.api.JcTypeVariableDeclaration
import org.utbot.jcdb.api.JcUnboundWildcard
import org.utbot.jcdb.api.ext.isNullable

class JcUnboundWildcardImpl(override val classpath: JcClasspath, override val nullable: Boolean = true) :
    JcUnboundWildcard {

    override val typeName: String
        get() = "*"

    override fun notNullable(): JcRefType {
        return JcUnboundWildcardImpl(classpath, false)
    }

    override fun relaxNullabilityWith(type: KmType): JcRefType {
        return JcUnboundWildcardImpl(classpath, type.isNullable)
    }
}

class JcBoundedWildcardImpl(
    override val upperBounds: List<JcRefType>,
    override val lowerBounds: List<JcRefType>,
    override val nullable: Boolean
) : JcBoundedWildcard {

    override val classpath: JcClasspath
        get() = upperBounds.firstOrNull()?.classpath ?: lowerBounds.firstOrNull()?.classpath
        ?: throw IllegalStateException("Upper or lower bound should be specified")

    override val typeName: String
        get() {
            val (name, bounds) = when{
                upperBounds.isNotEmpty() -> "extends" to upperBounds
                else -> "super" to lowerBounds
            }
            return "? $name ${bounds.joinToString(" & ") { it.typeName }}"
        }


    override fun notNullable(): JcRefType {
        return JcBoundedWildcardImpl(upperBounds, lowerBounds, false)
    }

    override fun relaxNullabilityWith(type: KmType): JcRefType {
        return if (upperBounds.isEmpty())
            JcBoundedWildcardImpl(emptyList(), listOf(lowerBounds.single().relaxNullabilityWith(type)), type.isNullable)
        else if (lowerBounds.isEmpty())
            JcBoundedWildcardImpl(listOf(upperBounds.single().relaxNullabilityWith(type)), emptyList(), type.isNullable)
        else
            error("Bounded wild card should have exactly one bound")
    }
}


class JcTypeVariableImpl(
    override val classpath: JcClasspath,
    private val declaration: JcTypeVariableDeclaration,
    override val nullable: Boolean
) : JcTypeVariable {

    override val typeName: String
        get() = symbol

    override val symbol: String get() = declaration.symbol

    override val bounds: List<JcRefType>
        get() = declaration.bounds

    override fun notNullable(): JcRefType {
        return JcTypeVariableImpl(classpath, declaration, nullable)
    }

    override fun relaxNullabilityWith(type: KmType): JcRefType {
        return JcTypeVariableImpl(classpath, declaration, type.isNullable)
    }
}