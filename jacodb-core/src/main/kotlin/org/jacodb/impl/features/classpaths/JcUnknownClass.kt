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

import org.jacodb.api.*
import org.jacodb.api.ext.jcdbName
import org.jacodb.impl.features.classpaths.AbstractJcResolvedResult.JcResolvedClassResultImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClassImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualFieldImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.JcTypedFieldImpl
import org.jacodb.impl.types.JcTypedMethodImpl
import org.jacodb.impl.types.TypeNameImpl
import org.jacodb.impl.types.substition.JcSubstitutor
import org.objectweb.asm.Type

class JcUnknownClass(override var classpath: JcClasspath, name: String) : JcVirtualClassImpl(
    name,
    initialFields = emptyList(),
    initialMethods = emptyList()
) {
    override val lookup: JcLookup<JcField, JcMethod> = JcUnknownClassLookup(this)
}

class JcUnknownMethod(
    enclosingClass: JcUnknownClass,
    name: String,
    description: String,
    returnType: TypeName,
    params: List<TypeName>
) : JcVirtualMethodImpl(
    name,
    returnType = returnType,
    parameters = params.mapIndexed { index, typeName -> JcVirtualParameter(index, typeName) },
    description = description
) {

    companion object {
        fun method(type: JcUnknownClass, name: String, description: String): JcMethod {
            val methodType = Type.getMethodType(description)
            val returnType = TypeNameImpl(methodType.returnType.className.jcdbName())
            val paramsType = methodType.argumentTypes.map { TypeNameImpl(it.className.jcdbName()) }
            return JcUnknownMethod(type, name, description, returnType, paramsType)
        }

        fun typedMethod(type: JcUnknownType, name: String, description: String): JcTypedMethod {
            return JcTypedMethodImpl(
                type,
                method(type.jcClass as JcUnknownClass, name, description),
                JcSubstitutor.empty
            )
        }
    }

    init {
        bind(enclosingClass)
    }
}

class JcUnknownField(enclosingClass: JcUnknownClass, name: String, type: TypeName) :
    JcVirtualFieldImpl(name, type = type) {

    companion object {

        fun typedField(type: JcClassType, name: String, fieldType: TypeName): JcTypedField {
            return JcTypedFieldImpl(
                type,
                JcUnknownField(type.jcClass as JcUnknownClass, name, fieldType),
                JcSubstitutor.empty
            )
        }

    }

    init {
        bind(enclosingClass)
    }
}

object UnknownClasses : JcClasspathExtFeature {
    override fun tryFindClass(classpath: JcClasspath, name: String): JcClasspathExtFeature.JcResolvedClassResult {
        return JcResolvedClassResultImpl(name, JcUnknownClass(classpath, name))
    }

    override fun tryFindType(classpath: JcClasspath, name: String): JcClasspathExtFeature.JcResolvedTypeResult {
        return AbstractJcResolvedResult.JcResolvedTypeResultImpl(name, JcUnknownType(classpath, name))
    }
}

val JcClasspath.isResolveAllToUnknown: Boolean get() = isInstalled(UnknownClasses)