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

package org.jacodb.impl.types

import org.jacodb.api.jvm.*
import org.jacodb.api.jvm.ext.packageName
import org.jacodb.impl.bytecode.JcAbstractLookup
import org.jacodb.impl.bytecode.PolymorphicSignatureSupport

class JcClassTypeLookupImpl(val type: JcClassType) : JcLookup<JcTypedField, JcTypedMethod> {

    override fun field(name: String, typeName: TypeName?): JcTypedField? {
        return JcClassTypeLookup.JcTypedFieldLookup(type, name).lookup()
    }

    override fun method(name: String, description: String): JcTypedMethod? {
        return JcClassTypeLookup.JcTypedMethodLookup(type, name, description).lookup()
    }

    override fun staticMethod(name: String, description: String): JcTypedMethod? {
        return JcClassTypeLookup.JcStaticTypedMethodLookup(type, name, description).lookup()
    }

    override fun specialMethod(name: String, description: String): JcTypedMethod? {
        return JcClassTypeLookup.JcSpecialTypedMethodLookup(type, name, description).lookup()
    }
}


abstract class JcClassTypeLookup<Result : JcAccessible>(clazz: JcClassType) :
    JcAbstractLookup<JcClassType, Result>(clazz) {

    override val JcClassType.resolvePackage: String
        get() = jcClass.packageName


    internal open class JcTypedMethodLookup(
        type: JcClassType,
        protected val name: String,
        protected val description: String,
    ) : JcClassTypeLookup<JcTypedMethod>(type), PolymorphicSignatureSupport {

        override fun JcClassType.next() = listOfNotNull(superType) + interfaces

        override val JcClassType.elements: List<JcTypedMethod>
            get() = declaredMethods

        override val predicate: (JcTypedMethod) -> Boolean
            get() = { it.name == name && it.method.description == description }

        override fun lookupElement(en: JcClassType): JcTypedMethod? {
            return super.lookupElement(en) ?: en.declaredMethods.find(name)
        }
    }


    internal class JcStaticTypedMethodLookup(
        type: JcClassType,
        name: String,
        description: String,
    ) : JcTypedMethodLookup(type, name, description) {

        override fun JcClassType.next() = listOfNotNull(superType)

        override val predicate: (JcTypedMethod) -> Boolean
            get() = { it.name == name && it.isStatic && it.method.description == description }

    }

    internal class JcSpecialTypedMethodLookup(
        type: JcClassType,
        name: String,
        description: String,
    ) : JcTypedMethodLookup(type, name, description) {

        override fun JcClassType.next() = listOfNotNull(superType)

        override val predicate: (JcTypedMethod) -> Boolean
            get() = { it.name == name && it.method.description == description }

    }

    internal class JcTypedFieldLookup(type: JcClassType, private val name: String) :
        JcClassTypeLookup<JcTypedField>(type) {

        override fun JcClassType.next() = listOfNotNull(superType) + interfaces

        override val JcClassType.elements: List<JcTypedField>
            get() = declaredFields

        override val predicate: (JcTypedField) -> Boolean
            get() = { it.name == name }


    }

}
