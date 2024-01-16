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

package org.jacodb.impl.features.classpaths.virtual

import org.jacodb.api.core.TypeName
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcDeclaration
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.impl.bytecode.JcDeclarationImpl
import org.objectweb.asm.Opcodes

interface JcVirtualField : JcField {
    fun bind(clazz: JcClassOrInterface)

}

open class JcVirtualFieldImpl(
    override val name: String,
    override val access: Int = Opcodes.ACC_PUBLIC,
    override val type: TypeName,
) : JcVirtualField {
    override val declaration: JcDeclaration
        get() = JcDeclarationImpl.of(enclosingClass.declaration.location, this)

    override lateinit var enclosingClass: JcClassOrInterface

    override fun bind(clazz: JcClassOrInterface) {
        this.enclosingClass = clazz
    }

    override val signature: String?
        get() = null
    override val annotations: List<JcAnnotation>
        get() = emptyList()

    override fun toString(): String {
        return "virtual $enclosingClass#$name"
    }
}