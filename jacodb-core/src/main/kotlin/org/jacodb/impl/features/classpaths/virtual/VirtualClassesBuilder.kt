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

import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.TypeName
import org.jacodb.api.ext.jvmName
import org.jacodb.impl.features.classpaths.VirtualClasses
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.Opcodes

open class VirtualClassesBuilder {
    open class VirtualClassBuilder(private var _name: String) {
        var access: Int = Opcodes.ACC_PUBLIC
            private set
        var fields: ArrayList<VirtualFieldBuilder> = ArrayList()
            private set
        var methods: ArrayList<VirtualMethodBuilder> = ArrayList()
            private set

        val name: String get() = _name

        fun access(code: Int) = apply {
            this.access = code
        }

        fun name(name: String) = apply {
            this._name = name
        }

        fun field(name: String, access: Int = Opcodes.ACC_PUBLIC, callback: VirtualFieldBuilder.() -> Unit = {}) =
            apply {
                fields.add(VirtualFieldBuilder(name).also {
                    it.access(access)
                    it.callback()
                })
            }

        fun method(name: String, access: Int = Opcodes.ACC_PUBLIC, callback: VirtualMethodBuilder.() -> Unit = {}) =
            apply {
                methods.add(VirtualMethodBuilder(name).also {
                    it.access(access)
                    it.callback()
                })
            }

        fun build(): JcVirtualClass {
            return JcVirtualClassImpl(
                _name,
                access,
                fields.map { it.build() },
                methods.map { it.build() },
            )
        }
    }

    open class VirtualFieldBuilder(private var _name: String = "_virtual_") {
        companion object {
            private val defType = TypeNameImpl("java.lang.Object")
        }

        val name: String get() = _name

        var access: Int = Opcodes.ACC_PUBLIC
            private set
        var type: TypeName = defType
            private set

        fun access(code: Int) = apply {
            this.access = code
        }

        fun type(name: String) = apply {
            type = TypeNameImpl(name)
        }

        fun name(name: String) = apply {
            this._name = name
        }

        open fun build(): JcVirtualField {
            return JcVirtualFieldImpl(_name, access, type)
        }

    }

    open class VirtualMethodBuilder(private var _name: String = "_virtual_") {
        val name: String get() = _name

        var access = Opcodes.ACC_PUBLIC
            private set
        var returnType: TypeName = TypeNameImpl(PredefinedPrimitives.Void)
            private set
        var parameters: List<TypeName> = emptyList()
            private set

        fun access(code: Int) = apply {
            this.access = code
        }

        fun params(vararg p: String) = apply {
            parameters = p.map { TypeNameImpl(it) }.toList()
        }

        fun name(name: String) = apply {
            this._name = name
        }

        fun returnType(name: String) = apply {
            returnType = TypeNameImpl(name)
        }

        open val description: String
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
                _name,
                access,
                returnType,
                parameters.mapIndexed { index, typeName -> JcVirtualParameter(index, typeName) },
                description
            )
        }
    }

    private val classes = ArrayList<VirtualClassBuilder>()

    fun virtualClass(name: String, access: Int = Opcodes.ACC_PUBLIC, callback: VirtualClassBuilder.() -> Unit = {}) =
        apply {
            classes.add(VirtualClassBuilder(name).also {
                it.access(access)
                it.callback()
            })
        }

    fun buildClasses() = classes.map { it.build() }
    fun build() = VirtualClasses(buildClasses())
}