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

import org.jacodb.api.common.CommonArrayType
import org.jacodb.api.common.CommonClassType
import org.jacodb.api.common.CommonRefType
import org.jacodb.api.common.CommonType
import org.jacodb.api.common.CommonTypedField
import org.jacodb.api.common.CommonTypedMethod
import org.jacodb.api.common.CommonTypedMethodParameter
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.objectClass
import org.objectweb.asm.tree.LocalVariableNode

interface JcTypedField : JcAccessible, CommonTypedField {
    override val field: JcField
    override val type: JcType
    val enclosingType: JcRefType
}

interface JcTypedMethod : JcAccessible, CommonTypedMethod<JcMethod, JcInst> {
    val name: String
    override val returnType: JcType

    val typeParameters: List<JcTypeVariableDeclaration>
    val typeArguments: List<JcRefType>

    override val parameters: List<JcTypedMethodParameter>
    val exceptions: List<JcRefType>
    override val method: JcMethod

    val enclosingType: JcRefType

    fun typeOf(inst: LocalVariableNode): JcType

}

interface JcTypedMethodParameter : CommonTypedMethodParameter {
    override val type: JcType
    override val name: String?
    override val enclosingMethod: JcTypedMethod
}

interface JcType : CommonType {
    val classpath: JcClasspath
    override val typeName: String

    override val nullable: Boolean?
    val annotations: List<JcAnnotation>

    fun copyWithAnnotations(annotations: List<JcAnnotation>): JcType
}

interface JcPrimitiveType : JcType {
    override val nullable: Boolean
        get() = false
}

interface JcRefType : JcType, CommonRefType {

    override val jcClass: JcClassOrInterface

    fun copyWithNullability(nullability: Boolean?): JcRefType
}

interface JcArrayType : JcRefType, CommonArrayType {
    override val elementType: JcType

    override val jcClass: JcClassOrInterface
        get() = classpath.objectClass

    override val dimensions: Int
}

interface JcClassType : JcRefType, JcAccessible, CommonClassType {

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

    /**
     * lookup instance for this class. Use it to resolve field/method references from bytecode instructions
     *
     * It's not necessary that looked up method will return instance preserved in [JcClassType.declaredFields] or
     * [JcClassType.declaredMethods] collections
     */
    val lookup: JcLookup<JcTypedField, JcTypedMethod>

}

interface JcTypeVariable : JcRefType {
    val symbol: String

    val bounds: List<JcRefType>
}

interface JcBoundedWildcard : JcRefType {
    val upperBounds: List<JcRefType>
    val lowerBounds: List<JcRefType>

    override fun copyWithAnnotations(annotations: List<JcAnnotation>): JcType = this
}

interface JcUnboundWildcard : JcRefType {
    override val jcClass: JcClassOrInterface
        get() = classpath.objectClass

    override fun copyWithAnnotations(annotations: List<JcAnnotation>): JcType = this

}

interface JcTypeVariableDeclaration {
    val symbol: String
    val bounds: List<JcRefType>
    val owner: JcAccessible
}
