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

import org.jacodb.api.JcAnnotation
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcDeclaration
import org.jacodb.api.JcMethod
import org.jacodb.api.JcParameter
import org.jacodb.api.TypeName
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.impl.bytecode.JcDeclarationImpl
import org.jacodb.impl.cfg.JcGraphImpl
import org.jacodb.impl.cfg.JcInstListImpl
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode

interface JcVirtualMethod : JcMethod {

    fun bind(clazz: JcClassOrInterface)

    override fun asmNode() = MethodNode()

    override val rawInstList: JcInstList<JcRawInst>
        get() = JcInstListImpl(emptyList())
    override val instList: JcInstList<JcInst>
        get() = JcInstListImpl(emptyList())

    override fun flowGraph(): JcGraph = JcGraphImpl(this, instList.instructions)
}

open class JcVirtualParameter(
    override val index: Int,
    override val type: TypeName
) : JcParameter {

    override val declaration: JcDeclaration
        get() = JcDeclarationImpl.of(method.enclosingClass.declaration.location, this)

    override val name: String?
        get() = null

    override val annotations: List<JcAnnotation>
        get() = emptyList()

    override val access: Int
        get() = Opcodes.ACC_PUBLIC

    override lateinit var method: JcMethod

    fun bind(method: JcVirtualMethod) {
        this.method = method
    }

}

open class JcVirtualMethodImpl(
    override val name: String,
    override val access: Int = Opcodes.ACC_PUBLIC,
    override val returnType: TypeName,
    override val parameters: List<JcVirtualParameter>,
    override val description: String
) : JcVirtualMethod {

    init {
        parameters.forEach { it.bind(this) }
    }

    override val declaration: JcDeclaration
        get() = JcDeclarationImpl.of(enclosingClass.declaration.location, this)

    override lateinit var enclosingClass: JcClassOrInterface

    override val signature: String?
        get() = null
    override val annotations: List<JcAnnotation>
        get() = emptyList()

    override val exceptions: List<TypeName>
        get() = emptyList()

    override fun bind(clazz: JcClassOrInterface) {
        enclosingClass = clazz
    }

    override fun toString(): String {
        return "virtual ${enclosingClass}#$name(${parameters.joinToString { it.type.typeName }})"
    }
}