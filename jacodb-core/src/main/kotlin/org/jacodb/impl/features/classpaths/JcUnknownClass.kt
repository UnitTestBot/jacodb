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

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspathExtFeature
import org.jacodb.api.jvm.JcLookup
import org.jacodb.api.jvm.ext.jcdbName
import org.jacodb.impl.features.classpaths.AbstractJcResolvedResult.JcResolvedClassResultImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClassImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualFieldImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.JcTypedFieldImpl
import org.jacodb.impl.types.JcTypedMethodImpl
import org.jacodb.impl.types.TypeNameImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcLookupExtFeature
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcProject
import org.jacodb.api.jvm.JcTypedField
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.core.TypeName
import org.objectweb.asm.Type

class JcUnknownClass(override var classpath: JcProject, name: String) : JcVirtualClassImpl(
    name,
    initialFields = emptyList(),
    initialMethods = emptyList()
) {
    override val lookup: JcLookup<JcField, JcMethod> = JcUnknownClassLookup(this)
}

class JcUnknownMethod(
    enclosingClass: JcClassOrInterface,
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
        fun method(type: JcClassOrInterface, name: String, description: String): JcMethod {
            val methodType = Type.getMethodType(description)
            val returnType = TypeNameImpl(methodType.returnType.className.jcdbName())
            val paramsType = methodType.argumentTypes.map { TypeNameImpl(it.className.jcdbName()) }
            return JcUnknownMethod(type, name, description, returnType, paramsType)
        }

        fun typedMethod(type: JcClassType, name: String, description: String): JcTypedMethod {
            return JcTypedMethodImpl(
                type,
                method(type.jcClass, name, description),
                JcSubstitutorImpl.empty
            )
        }
    }

    init {
        bind(enclosingClass)
    }
}

class JcUnknownField(enclosingClass: JcClassOrInterface, name: String, type: TypeName) :
    JcVirtualFieldImpl(name, type = type) {

    companion object {

        fun typedField(type: JcClassType, name: String, fieldType: TypeName): JcTypedField {
            return JcTypedFieldImpl(
                type,
                JcUnknownField(type.jcClass, name, fieldType),
                JcSubstitutorImpl.empty
            )
        }

    }

    init {
        bind(enclosingClass)
    }
}

/**
 * Feature for mocking references to unknown classes. I.e let's assume that we have:
 *
 * ```
 * class Bar {
 *
 *      int x = 0;
 *
 *      public void run() {
 *          System.out.println("Hello world");
 *      }
 * }
 *
 * class Foo extends Bar {
 *
 *      Bar f = new Bar();
 *
 *      public void call() {
 *          System.out.println(f.x);
 *          run();
 *      }
 * }
 * ```
 *
 * Let's assume that we have classpath that contains class `Foo` and doesn't contain `Bar`. Default behavior for
 * classpath is to fail on trying to access class that doesn't exist. i.e parsing method instructions will fail, reading
 * class hierarchy will fail, resolving method will fail.
 *
 * UnknownClasses feature fix this behaviour. All references pointing to nowhere will be resolved as special implementation
 * of [JcClassOrInterface] instance. Such instance will have **empty** [JcClassOrInterface.declaredFields] and
 * [JcClassOrInterface.declaredMethods] but all resolutions done through [JcClassOrInterface.lookup] interface will return
 * mocked instances
 *
 */
object UnknownClasses : JcClasspathExtFeature {

    private val location = VirtualLocation()

    override fun tryFindClass(classpath: JcProject, name: String): JcClasspathExtFeature.JcResolvedClassResult {
        return JcResolvedClassResultImpl(name, JcUnknownClass(classpath, name).also {
            it.bind(classpath, location)
        })
    }

    override fun tryFindType(classpath: JcProject, name: String): JcClasspathExtFeature.JcResolvedTypeResult {
        return AbstractJcResolvedResult.JcResolvedTypeResultImpl(name, JcUnknownType(classpath, name, location))
    }
}

/**
 * Used for mocking of methods and fields refs that doesn't exist in code base of classpath
 * ```
 * class Bar {
 *
 *      int x = 0;
 *
 *      public void run() {
 *          System.out.println("Hello world");
 *      }
 * }
 *
 * class Foo extends Bar {
 *
 *      Bar f = new Bar();
 *
 *      public void call() {
 *          System.out.println(f.y);
 *          f.runSomething();
 *      }
 * }
 * ```
 *
 * 3-address representation of bytecode for Foo class can't resolve `Bar#y` field and `Bar#runSomething`
 * method by default. With this feature such methods and fields will be resolved as JcUnknownField and JcUnknownMethod
 */
object UnknownClassMethodsAndFields : JcLookupExtFeature {

    override fun lookup(clazz: JcClassOrInterface): JcLookup<JcField, JcMethod> {
        return JcUnknownClassLookup(clazz)
    }

    override fun lookup(type: JcClassType): JcLookup<JcTypedField, JcTypedMethod> {
        return JcUnknownTypeLookup(type)
    }
}


val JcProject.isResolveAllToUnknown: Boolean get() = isInstalled(UnknownClasses)