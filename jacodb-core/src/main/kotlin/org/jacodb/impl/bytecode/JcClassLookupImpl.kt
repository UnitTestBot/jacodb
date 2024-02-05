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

package org.jacodb.impl.bytecode

import org.jacodb.api.JcAccessible
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcField
import org.jacodb.api.JcLookup
import org.jacodb.api.JcMethod
import org.jacodb.api.TypeName
import org.jacodb.api.ext.packageName

class JcClassLookupImpl(val clazz: JcClassOrInterface) : JcLookup<JcField, JcMethod> {

    override fun field(name: String, typeName: TypeName?): JcField? {
        return JcClassLookup.JcFieldLookup(clazz, name).lookup()
    }

    override fun method(name: String, description: String): JcMethod? {
        return JcClassLookup.JcMethodLookup(clazz, name, description).lookup()
    }

    override fun staticMethod(name: String, description: String): JcMethod? {
        return JcClassLookup.JcStaticMethodLookup(clazz, name, description).lookup()
    }

    override fun specialMethod(name: String, description: String): JcMethod? {
        return JcClassLookup.JcSpecialMethodLookup(clazz, name, description).lookup()
    }

}

internal abstract class JcClassLookup<Result : JcAccessible>(clazz: JcClassOrInterface) :
    JcAbstractLookup<JcClassOrInterface, Result>(clazz) {

    override val JcClassOrInterface.resolvePackage: String
        get() = packageName

    internal open class JcMethodLookup(
        clazz: JcClassOrInterface,
        protected val name: String,
        protected val description: String,
    ) : JcClassLookup<JcMethod>(clazz), PolymorphicSignatureSupport {

        override val JcClassOrInterface.elements: List<JcMethod>
            get() = entry.declaredMethods

        override fun JcClassOrInterface.next() = listOfNotNull(entry.superClass) + entry.interfaces

        override val predicate: (JcMethod) -> Boolean
            get() = { it.name == name && it.description == description }

        override fun lookupElement(en: JcClassOrInterface): JcMethod? {
            return super.lookupElement(en) ?: en.declaredMethods.find(en.name, description)
        }

    }

    internal class JcStaticMethodLookup(
        clazz: JcClassOrInterface,
        name: String,
        description: String,
    ) : JcMethodLookup(clazz, name, description) {

        override fun JcClassOrInterface.next() = listOfNotNull(entry.superClass)

        override val predicate: (JcMethod) -> Boolean
            get() = { it.name == name && it.isStatic && it.description == description }

    }

    internal class JcSpecialMethodLookup(
        clazz: JcClassOrInterface,
        name: String,
        description: String,
    ) : JcMethodLookup(clazz, name, description) {

        override fun JcClassOrInterface.next() = emptyList<JcClassOrInterface>()

        override val predicate: (JcMethod) -> Boolean
            get() = { it.name == name && it.isStatic && it.description == description }

    }

    internal class JcFieldLookup(clazz: JcClassOrInterface, private val name: String) : JcClassLookup<JcField>(clazz) {

        override val JcClassOrInterface.elements: List<JcField>
            get() = entry.declaredFields

        override fun JcClassOrInterface.next() = listOfNotNull(superClass) + interfaces

        override val predicate: (JcField) -> Boolean
            get() = { it.name == name }

    }

}
