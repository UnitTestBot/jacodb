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

package org.jacodb.impl.features.classpaths

import org.jacodb.api.JcAnnotation
import org.jacodb.api.JcByteCodeLocation
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathFeature
import org.jacodb.api.JcDeclaration
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcParameter
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.RegisteredLocation
import org.jacodb.api.TypeName
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.api.ext.isInterface
import org.jacodb.api.ext.jvmName
import org.jacodb.api.ext.objectClass
import org.jacodb.impl.bytecode.JcDeclarationImpl
import org.jacodb.impl.cfg.JcGraphBuilder
import org.jacodb.impl.cfg.JcInstListImpl
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

class VirtualClasses(
    val classes: List<JcVirtualClass>,
    private val virtualLocation: VirtualLocation = VirtualLocation()
) : JcClasspathFeature {

    companion object {

        fun create(factory: VirtualClassesBuilder.() -> Unit): VirtualClasses {
            return VirtualClasses(VirtualClassesBuilder().also { it.factory() }.build())
        }

    }

    private val map = classes.associateBy { it.name }

    override fun tryFindClass(classpath: JcClasspath, name: String): JcClassOrInterface? {
        return map[name]?.also {
            it.bind(classpath, virtualLocation)
        }
    }

}

interface JcVirtualClass : JcClassOrInterface {
    override val declaredFields: List<JcVirtualField>
    override val declaredMethods: List<JcVirtualMethod>

    fun bind(classpath: JcClasspath, virtualLocation: VirtualLocation) {
    }
}

interface JcVirtualField : JcField {
    fun bind(clazz: JcClassOrInterface)

}

interface JcVirtualMethod : JcMethod {

    fun bind(clazz: JcClassOrInterface)

    override fun body() = MethodNode()

    override val rawInstList: JcInstList<JcRawInst>
        get() = JcInstListImpl(emptyList())
    override val instList: JcInstList<JcInst>
        get() = JcInstListImpl(emptyList())

    override fun flowGraph(): JcGraph {
        return JcGraphBuilder(this, rawInstList).buildFlowGraph()
    }
}

class VirtualLocation : RegisteredLocation {
    override val jcLocation: JcByteCodeLocation?
        get() = null

    override val id: Long
        get() = -1

    override val path: String = "/dev/null"

    override val isRuntime: Boolean
        get() = false

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

    override val exceptions: List<JcClassOrInterface>
        get() = emptyList()

    override fun bind(clazz: JcClassOrInterface) {
        enclosingClass = clazz
    }
}


open class VirtualClassesBuilder {
    open class VirtualClassBuilder(var name: String) {
        var access: Int = Opcodes.ACC_PUBLIC
        var fields: ArrayList<VirtualFieldBuilder> = ArrayList()
        var methods: ArrayList<VirtualMethodBuilder> = ArrayList()

        fun newField(name: String, access: Int = Opcodes.ACC_PUBLIC, callback: VirtualFieldBuilder.() -> Unit = {}) {
            fields.add(VirtualFieldBuilder(name).also {
                it.access = access
                it.callback()
            })
        }

        fun newMethod(name: String, access: Int = Opcodes.ACC_PUBLIC, callback: VirtualMethodBuilder.() -> Unit = {}) {
            methods.add(VirtualMethodBuilder(name).also {
                it.access = access
                it.callback()
            })
        }

        fun build(): JcVirtualClass {
            return JcVirtualClassImpl(
                name,
                access,
                fields.map { it.build() },
                methods.map { it.build() },
            )
        }
    }

    open class VirtualFieldBuilder(var name: String) {
        companion object {
            private val defType = TypeNameImpl("java.lang.Object")
        }

        var access: Int = Opcodes.ACC_PUBLIC
        var type: TypeName = defType

        fun type(name: String) {
            type = TypeNameImpl(name)
        }

        fun build(): JcVirtualField {
            return JcVirtualFieldImpl(name, access, type)
        }

    }

    open class VirtualMethodBuilder(val name: String) {

        var access = Opcodes.ACC_PUBLIC
        var returnType: TypeName = TypeNameImpl(PredefinedPrimitives.Void)
        var parameters: List<TypeName> = emptyList()

        fun params(vararg p: String) {
            parameters = p.map { TypeNameImpl(it) }.toList()
        }

        fun returnType(name: String) {
            returnType = TypeNameImpl(name)
        }

        val description: String
            get() {
                return buildString {
                    append("(")
                    parameters.forEach {
                        append(it.typeName.jvmName())
                    }
                    append(")")
                    append(returnType.typeName.jvmName())
                }
            }

        open fun build(): JcVirtualMethod {
            return JcVirtualMethodImpl(
                name,
                access,
                returnType,
                parameters.mapIndexed { index, typeName -> JcVirtualParameter(index, typeName) },
                description
            )
        }
    }

    private val classes = ArrayList<VirtualClassBuilder>()

    fun newClass(name: String, access: Int = Opcodes.ACC_PUBLIC, callback: VirtualClassBuilder.() -> Unit = {}) {
        classes.add(VirtualClassBuilder(name).also {
            it.access = access
            it.callback()
        })
    }

    fun build() = classes.map { it.build() }
}