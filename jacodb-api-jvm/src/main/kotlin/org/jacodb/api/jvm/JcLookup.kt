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

package org.jacodb.api.jvm

/**
 * lookup for fields and methods in [JcClassOrInterface] and [JcClassType].
 *
 * It's not necessary that looked up method will return instance preserved in [JcClassOrInterface.declaredFields] or
 * [JcClassOrInterface.declaredMethods] collections
 */
@JvmDefaultWithoutCompatibility
interface JcLookup<Field : JcAccessible, Method : JcAccessible> {

    /**
     * lookup for field with specific name
     * @param name of field
     */
    fun field(name: String): Field? = field(name, null)

    /**
     * lookup for field with specific name and expected type. Used during instructions parsing. In this case field type is preserved
     * in Java bytecode
     *
     * @param name of field
     * @param typeName expected type of field
     */
    fun field(name: String, typeName: TypeName?): Field?

    /**
     * Lookup for method based on name and description:
     * - in current class search for private methods too
     * - in parent classes and interfaces search only for visible methods
     *
     * @param name method name
     * @param description jvm description of method
     */
    fun method(name: String, description: String): Method?

    /**
     * Lookup for static method based on name and description
     *
     * @param name method name
     * @param description jvm description of method
     */
    fun staticMethod(name: String, description: String): Method?

    /**
     * Lookup for methods placed in special instructions i.e `private` and `super` calls.
     *
     * @param name method name
     * @param description jvm description of method
     */
    fun specialMethod(name: String, description: String): Method?
}
