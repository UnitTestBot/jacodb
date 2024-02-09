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
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.JcLookup
import org.jacodb.api.JcMethod
import org.jacodb.api.JcRefType
import org.jacodb.api.JcTypeVariableDeclaration
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.TypeName
import org.jacodb.api.ext.objectType
import org.jacodb.impl.cfg.util.OBJECT_CLASS
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.Opcodes

class JcUnknownType(
    override var classpath: JcClasspath,
    private val name: String,
    private val location: VirtualLocation,
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

    override val nullable: Boolean
        get() = true
    override val annotations: List<JcAnnotation> = emptyList()

    override fun copyWithAnnotations(annotations: List<JcAnnotation>) = this

    override fun copyWithNullability(nullability: Boolean?) = this

    override val access: Int
        get() = Opcodes.ACC_PUBLIC
}

open class JcUnknownClassLookup(val clazz: JcClassOrInterface) : JcLookup<JcField, JcMethod> {

    override fun specialMethod(name: String, description: String): JcMethod = method(name, description)
    override fun staticMethod(name: String, description: String): JcMethod = method(name, description)

    override fun field(name: String, typeName: TypeName?): JcField {
        return JcUnknownField(clazz, name, typeName ?: TypeNameImpl(OBJECT_CLASS))
    }

    override fun method(name: String, description: String): JcMethod {
        return JcUnknownMethod.method(clazz, name, description)
    }

}

open class JcUnknownTypeLookup(val type: JcClassType) : JcLookup<JcTypedField, JcTypedMethod> {

    override fun specialMethod(name: String, description: String): JcTypedMethod = method(name, description)
    override fun staticMethod(name: String, description: String): JcTypedMethod = method(name, description)

    override fun field(name: String, typeName: TypeName?): JcTypedField {
        return JcUnknownField.typedField(type, name, typeName ?: TypeNameImpl(OBJECT_CLASS))
    }

    override fun method(name: String, description: String): JcTypedMethod {
        return JcUnknownMethod.typedMethod(type, name, description)
    }

}
