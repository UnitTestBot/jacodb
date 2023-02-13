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
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcDeclaration
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.isInterface
import org.jacodb.api.ext.objectClass
import org.jacodb.impl.bytecode.JcDeclarationImpl
import org.jacodb.impl.features.classpaths.VirtualLocation
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

interface JcVirtualClass : JcClassOrInterface {
    override val declaredFields: List<JcVirtualField>
    override val declaredMethods: List<JcVirtualMethod>

    fun bind(classpath: JcClasspath, virtualLocation: VirtualLocation) {
    }
}

open class JcVirtualClassImpl(
    override val name: String,
    override val access: Int = Opcodes.ACC_PUBLIC,
    override val declaredFields: List<JcVirtualField>,
    override val declaredMethods: List<JcVirtualMethod>
) : JcVirtualClass {

    init {
        declaredFields.forEach { it.bind(this) }
        declaredMethods.forEach { it.bind(this) }
    }

    private lateinit var virtualLocation: VirtualLocation

    override val declaration: JcDeclaration
        get() = JcDeclarationImpl.of(virtualLocation, this)

    override val annotations: List<JcAnnotation>
        get() = emptyList()

    override val signature: String?
        get() = null

    override val outerClass: JcClassOrInterface?
        get() = null

    override val innerClasses: List<JcClassOrInterface>
        get() = emptyList()

    override val interfaces: List<JcClassOrInterface>
        get() = emptyList()

    override val simpleName: String get() = name.substringAfterLast(".")

    override fun bytecode(): ClassNode {
        throw IllegalStateException("Can't get ASM node for Virtual class")
    }

    override val isAnonymous: Boolean
        get() = false

    override fun binaryBytecode(): ByteArray {
        throw IllegalStateException("Can't get bytecode for Virtual class")
    }

    override val superClass: JcClassOrInterface?
        get() = when (isInterface) {
            true -> null
            else -> classpath.objectClass
        }

    override val outerMethod: JcMethod?
        get() = null

    override lateinit var classpath: JcClasspath

    override fun bind(classpath: JcClasspath, virtualLocation: VirtualLocation) {
        this.classpath = classpath
        this.virtualLocation = virtualLocation
    }

}