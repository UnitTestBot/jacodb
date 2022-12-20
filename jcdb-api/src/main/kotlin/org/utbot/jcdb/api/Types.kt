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

package org.utbot.jcdb.api

import org.objectweb.asm.tree.LocalVariableNode

interface JcTypedField {
    val name: String

    val field: JcField
    val fieldType: JcType
    val enclosingType: JcRefType
}

interface JcTypedMethod {
    val name: String
    val returnType: JcType

    val typeParameters: List<JcTypeVariableDeclaration>
    val typeArguments: List<JcRefType>

    val parameters: List<JcTypedMethodParameter>
    val exceptions: List<JcClassOrInterface>
    val method: JcMethod

    val enclosingType: JcRefType

    fun typeOf(inst: LocalVariableNode): JcType

}

interface JcTypedMethodParameter {
    val type: JcType
    val name: String?
    val enclosingMethod: JcTypedMethod
    val nullable: Boolean?
}

interface JcType {
    val classpath: JcClasspath
    val typeName: String

    val nullable: Boolean?
}

interface JcPrimitiveType : JcType {
    override val nullable: Boolean
        get() = false
}

interface JcRefType : JcType {
    fun copyWithNullability(nullability: Boolean?): JcRefType
}

interface JcArrayType : JcRefType {
    val elementType: JcType
}

interface JcClassType : JcRefType {

    val jcClass: JcClassOrInterface

    val outerType: JcClassType?

    val declaredMethods: List<JcTypedMethod>
    val methods: List<JcTypedMethod>

    val declaredFields: List<JcTypedField>
    val fields: List<JcTypedField>

    val typeParameters: List<JcTypeVariableDeclaration>
    val typeArguments: List<JcRefType>

    val superType: JcClassType?
    val interfaces: List<JcClassType>

    val innerTypes: List<JcClassType>
}

interface JcTypeVariable : JcRefType {
    val symbol: String

    val bounds: List<JcRefType>
}

interface JcBoundedWildcard : JcRefType {
    val upperBounds: List<JcRefType>
    val lowerBounds: List<JcRefType>
}

interface JcUnboundWildcard : JcRefType

interface JcTypeVariableDeclaration {
    val symbol: String
    val bounds: List<JcRefType>
    val owner: JcAccessible
}