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

import org.utbot.jcdb.api.JcArrayType
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType

class JcArrayTypeImpl(
    override val elementType: JcType,
    override val nullable: Boolean = true
) : JcArrayType {

    override val typeName = elementType.typeName + "[]"

    override fun notNullable(): JcRefType {
        return JcArrayTypeImpl(elementType, false)
    }

    override val classpath: JcClasspath
        get() = elementType.classpath

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcArrayTypeImpl

        if (elementType != other.elementType) return false

        return true
    }

    override fun hashCode(): Int {
        return elementType.hashCode()
    }

}