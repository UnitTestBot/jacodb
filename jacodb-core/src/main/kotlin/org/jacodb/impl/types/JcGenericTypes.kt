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

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcBoundedWildcard
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypeVariable
import org.jacodb.api.jvm.JcTypeVariableDeclaration
import org.jacodb.api.jvm.JcUnboundWildcard
import org.jacodb.api.jvm.ext.objectClass
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JcUnboundWildcardImpl(override val classpath: JcClasspath) :
    JcUnboundWildcard {

    override val nullable: Boolean = true

    override val annotations: List<JcAnnotation> = listOf()

    override val typeName: String
        get() = "*"

    override fun copyWithNullability(nullability: Boolean?): JcRefType {
        if (nullability != true)
            error("Attempting to make wildcard not-nullable, which are always nullable by convention")
        return this
    }
}

class JcBoundedWildcardImpl(
    override val upperBounds: List<JcRefType>,
    override val lowerBounds: List<JcRefType>,
) : JcBoundedWildcard {
    override val nullable: Boolean = true

    override val annotations: List<JcAnnotation> = listOf()

    override val classpath: JcClasspath
        get() = upperBounds.firstOrNull()?.classpath ?: lowerBounds.firstOrNull()?.classpath
        ?: throw IllegalStateException("Upper or lower bound should be specified")

    override val typeName: String
        get() {
            val (name, bounds) = when {
                upperBounds.isNotEmpty() -> "extends" to upperBounds
                else -> "super" to lowerBounds
            }
            return "? $name ${bounds.joinToString(" & ") { it.typeName }}"
        }

    override val jcClass: JcClassOrInterface by lazy(PUBLICATION) {
        val obj = classpath.objectClass
        lowerBounds.firstNotNullOfOrNull { it.jcClass.takeIf { it != obj } } ?: obj
    }

    override fun copyWithNullability(nullability: Boolean?): JcRefType {
        if (nullability != true)
            error("Attempting to make wildcard not-nullable, which are always nullable by convention")
        return this
    }
}


class JcTypeVariableImpl(
    override val classpath: JcClasspath,
    private val declaration: JcTypeVariableDeclaration,
    override val nullable: Boolean?,
    override val annotations: List<JcAnnotation> = listOf()
) : JcTypeVariable {

    override val typeName: String
        get() = symbol

    override val symbol: String get() = declaration.symbol

    override val bounds: List<JcRefType>
        get() = declaration.bounds

    override val jcClass: JcClassOrInterface by lazy(PUBLICATION) {
        val obj = classpath.objectClass
        bounds.firstNotNullOfOrNull { it.jcClass.takeIf { it != obj } } ?: obj
    }

    override fun copyWithNullability(nullability: Boolean?): JcRefType {
        return JcTypeVariableImpl(classpath, declaration, nullability, annotations)
    }

    override fun copyWithAnnotations(annotations: List<JcAnnotation>): JcType =
        JcTypeVariableImpl(classpath, declaration, nullable, annotations)
}
