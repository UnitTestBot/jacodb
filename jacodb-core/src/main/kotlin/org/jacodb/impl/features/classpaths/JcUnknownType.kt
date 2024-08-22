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

import org.jacodb.api.jvm.*
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.impl.cfg.util.OBJECT_CLASS
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.Opcodes


class JcUnknownType(
    override var classpath: JcClasspath,
    private val name: String,
    private val location: VirtualLocation,
    override var nullable: Boolean
) : JcClassType {

    override val lookup: JcLookup<JcTypedField, JcTypedMethod> = JcUnknownTypeLookup(this)

    override val jcClass: JcClassOrInterface = JcUnknownClass(classpath, name).also {
        it.bind(classpath, location)
    }

    override val outerType: JcClassType? = null
    override val declaredMethods: List<JcTypedMethod> = emptyList()
    override val methods: List<JcTypedMethod> = emptyList()
    override val declaredFields: List<JcTypedField> = emptyList()
    override val fields: List<JcTypedField> = emptyList()
    override val typeParameters: List<JcTypeVariableDeclaration> = emptyList()
    override val typeArguments: List<JcRefType> = emptyList()
    override val superType: JcClassType get() = classpath.objectType
    override val interfaces: List<JcClassType> = emptyList()
    override val innerTypes: List<JcClassType> = emptyList()

    override val typeName: String
        get() = name

    override val annotations: List<JcAnnotation> = emptyList()

    override fun copyWithAnnotations(annotations: List<JcAnnotation>) = this

    override fun copyWithNullability(nullability: Boolean?) = this

    override val access: Int
        get() = Opcodes.ACC_PUBLIC

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is JcUnknownType && other.name == name
    }

    override fun hashCode(): Int = name.hashCode()
}

open class JcUnknownClassLookup(val clazz: JcClassOrInterface) : JcLookup<JcField, JcMethod> {

    override fun specialMethod(name: String, description: String): JcMethod =
        JcUnknownMethod.method(clazz, name, access = Opcodes.ACC_PUBLIC, description)

    override fun staticMethod(name: String, description: String): JcMethod =
        JcUnknownMethod.method(clazz, name, access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, description)

    override fun field(name: String, typeName: TypeName?, fieldKind: JcLookup.FieldKind): JcField {
        val staticModifier = if (fieldKind == JcLookup.FieldKind.STATIC) Opcodes.ACC_STATIC else 0
        val fieldType = typeName ?: TypeNameImpl(OBJECT_CLASS)
        return JcUnknownField(clazz, name, access = Opcodes.ACC_PUBLIC or staticModifier, fieldType)
    }

    override fun method(name: String, description: String): JcMethod {
        return JcUnknownMethod.method(clazz, name, access = Opcodes.ACC_PUBLIC, description)
    }

}

open class JcUnknownTypeLookup(val type: JcClassType) : JcLookup<JcTypedField, JcTypedMethod> {

    override fun specialMethod(name: String, description: String): JcTypedMethod =
        JcUnknownMethod.typedMethod(type, name, access = Opcodes.ACC_PUBLIC, description)

    override fun staticMethod(name: String, description: String): JcTypedMethod =
        JcUnknownMethod.typedMethod(type, name, access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, description)

    override fun field(name: String, typeName: TypeName?, fieldKind: JcLookup.FieldKind): JcTypedField {
        val staticModifier = if (fieldKind == JcLookup.FieldKind.STATIC) Opcodes.ACC_STATIC else 0
        val fieldType = typeName ?: TypeNameImpl(OBJECT_CLASS)
        return JcUnknownField.typedField(type, name, access = Opcodes.ACC_PUBLIC or staticModifier, fieldType)
    }

    override fun method(name: String, description: String): JcTypedMethod {
        return JcUnknownMethod.typedMethod(type, name, access = Opcodes.ACC_PUBLIC, description)
    }

}
