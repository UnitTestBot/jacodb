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
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathExtFeature
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcLookup
import org.jacodb.api.jvm.JcLookupExtFeature
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcTypedField
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.TypeName
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
import org.objectweb.asm.Type

class JcUnknownClass(override var classpath: JcClasspath, name: String) : JcVirtualClassImpl(
    name,
    initialFields = emptyList(),
    initialMethods = emptyList()
) {
    override val lookup: JcLookup<JcField, JcMethod> = JcUnknownClassLookup(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is JcUnknownClass && other.name == name
    }

    override fun hashCode(): Int = name.hashCode()
}

class JcUnknownMethod(
    enclosingClass: JcClassOrInterface,
    name: String,
    access: Int,
    description: String,
    returnType: TypeName,
    params: List<TypeName>
) : JcVirtualMethodImpl(
    name,
    access,
    returnType = returnType,
    parameters = params.mapIndexed { index, typeName -> JcVirtualParameter(index, typeName) },
    description = description
) {

    companion object {

        fun method(type: JcClassOrInterface, name: String, access: Int, description: String): JcMethod {
            val methodType = Type.getMethodType(description)
            val returnType = TypeNameImpl(methodType.returnType.className.jcdbName())
            val paramsType = methodType.argumentTypes.map { TypeNameImpl(it.className.jcdbName()) }
            return JcUnknownMethod(type, name, access, description, returnType, paramsType)
        }

        fun typedMethod(type: JcClassType, name: String, access: Int, description: String): JcTypedMethod {
            return JcTypedMethodImpl(
                type,
                method(type.jcClass, name, access, description),
                JcSubstitutorImpl.empty
            )
        }
    }

    init {
        bind(enclosingClass)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is JcUnknownMethod && description == other.description
    }

    override fun hashCode(): Int = description.hashCode()
}

class JcUnknownField(enclosingClass: JcClassOrInterface, name: String, access: Int, type: TypeName) :
    JcVirtualFieldImpl(name, access, type = type) {

    companion object {

        fun typedField(type: JcClassType, name: String, access: Int, fieldType: TypeName): JcTypedField {
            return JcTypedFieldImpl(
                type,
                JcUnknownField(type.jcClass, name, access, fieldType),
                JcSubstitutorImpl.empty
            )
        }

    }

    init {
        bind(enclosingClass)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is JcUnknownField && enclosingClass == other.enclosingClass && name == other.name
    }

    override fun hashCode(): Int = enclosingClass.hashCode() * 31 + name.hashCode()


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

    override fun tryFindClass(classpath: JcClasspath, name: String): JcClasspathExtFeature.JcResolvedClassResult {
        return JcResolvedClassResultImpl(name, JcUnknownClass(classpath, name).also {
            it.bind(classpath, location)
        })
    }

    override fun tryFindType(
        classpath: JcClasspath,
        name: String,
        nullable: Boolean?
    ): JcClasspathExtFeature.JcResolvedTypeResult {
        return AbstractJcResolvedResult.JcResolvedTypeResultImpl(
            name,
            JcUnknownType(classpath, name, location, nullable ?: true)
        )
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
        if (clazz !is JcUnknownClass) {
            return TrivialLookup
        }
        return JcUnknownClassLookup(clazz)
    }

    override fun lookup(type: JcClassType): JcLookup<JcTypedField, JcTypedMethod> {
        return JcUnknownTypeLookup(type)
    }
}


val JcClasspath.isResolveAllToUnknown: Boolean get() = isInstalled(UnknownClasses)

private object TrivialLookup : JcLookup<JcField, JcMethod> {

    override fun field(name: String, typeName: TypeName?, fieldKind: JcLookup.FieldKind): JcField? = null

    override fun method(name: String, description: String): JcMethod? = null

    override fun staticMethod(name: String, description: String): JcMethod? = null

    override fun specialMethod(name: String, description: String): JcMethod? = null
}

