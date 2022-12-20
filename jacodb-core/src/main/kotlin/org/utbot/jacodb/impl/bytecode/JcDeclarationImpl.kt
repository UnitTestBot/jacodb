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

package org.utbot.jacodb.impl.bytecode

import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcDeclaration
import org.utbot.jacodb.api.JcField
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.JcParameter
import org.utbot.jacodb.api.RegisteredLocation

class JcDeclarationImpl(override val location: RegisteredLocation, override val relativePath: String) : JcDeclaration {

    companion object {
        fun of(location: RegisteredLocation, clazz: JcClassOrInterface): JcDeclarationImpl {
            return JcDeclarationImpl(location, clazz.name)
        }

        fun of(location: RegisteredLocation, method: JcMethod): JcDeclarationImpl {
            return JcDeclarationImpl(location, "${method.enclosingClass.name}#${method.name}")
        }

        fun of(location: RegisteredLocation, field: JcField): JcDeclarationImpl {
            return JcDeclarationImpl(location, "${field.enclosingClass.name}#${field.name}")
        }

        fun of(location: RegisteredLocation, param: JcParameter): JcDeclarationImpl {
            return JcDeclarationImpl(location, "${param.method.enclosingClass.name}#${param.name}:${param.index}")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcDeclarationImpl

        if (location != other.location) return false
        if (relativePath != other.relativePath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = location.hashCode()
        result = 31 * result + relativePath.hashCode()
        return result
    }

}