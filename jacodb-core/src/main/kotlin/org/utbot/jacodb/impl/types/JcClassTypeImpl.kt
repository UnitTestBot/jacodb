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

package org.utbot.jacodb.impl.types

import org.utbot.jacodb.api.JcAnnotation
import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcClassType
import org.utbot.jacodb.api.JcRefType
import org.utbot.jacodb.api.JcType
import org.utbot.jacodb.api.JcTypedField
import org.utbot.jacodb.api.JcTypedMethod
import org.utbot.jacodb.api.ext.isConstructor
import org.utbot.jacodb.api.ext.isPackagePrivate
import org.utbot.jacodb.api.ext.isProtected
import org.utbot.jacodb.api.ext.isPublic
import org.utbot.jacodb.api.ext.isStatic
import org.utbot.jacodb.api.ext.packageName
import org.utbot.jacodb.api.ext.toType
import org.utbot.jacodb.impl.types.signature.JvmClassRefType
import org.utbot.jacodb.impl.types.signature.JvmParameterizedType
import org.utbot.jacodb.impl.types.signature.JvmType
import org.utbot.jacodb.impl.types.signature.TypeResolutionImpl
import org.utbot.jacodb.impl.types.signature.TypeSignature
import org.utbot.jacodb.impl.types.substition.JcSubstitutor
import org.utbot.jacodb.impl.types.substition.substitute

open class JcClassTypeImpl(
    override val jcClass: JcClassOrInterface,
    override val outerType: JcClassTypeImpl? = null,
    private val substitutor: JcSubstitutor = JcSubstitutor.empty,
    override val nullable: Boolean?,
    override val annotations: List<JcAnnotation>
) : JcClassType {

    constructor(
        jcClass: JcClassOrInterface,
        outerType: JcClassTypeImpl? = null,
        parameters: List<JvmType>,
        nullable: Boolean?,
        annotations: List<JcAnnotation>
    ) : this(jcClass, outerType, jcClass.substitute(parameters, outerType?.substitutor), nullable, annotations)

    private val resolutionImpl by lazy(LazyThreadSafetyMode.NONE) { TypeSignature.withDeclarations(jcClass) as? TypeResolutionImpl }
    private val declaredTypeParameters by lazy(LazyThreadSafetyMode.NONE) { jcClass.typeParameters }

    override val classpath get() = jcClass.classpath

    override val typeName: String by lazy {
        val generics = if (substitutor.substitutions.isEmpty()) {
            declaredTypeParameters.joinToString { it.symbol }
        } else {
            declaredTypeParameters.joinToString {
                substitutor.substitution(it)?.displayName ?: it.symbol
            }
        }
        val outer = outerType
        val name = if (outer != null) {
            outer.typeName + "." + jcClass.simpleName
        } else {
            jcClass.name
        }
        name + ("<${generics}>".takeIf { generics.isNotEmpty() } ?: "")
    }

    override val typeParameters get() = declaredTypeParameters.map { it.asJcDeclaration(jcClass) }

    override val typeArguments: List<JcRefType>
        get() {
            return declaredTypeParameters.map { declaration ->
                val jvmType = substitutor.substitution(declaration)
                if (jvmType != null) {
                    classpath.typeOf(jvmType) as JcRefType
                } else {
                    JcTypeVariableImpl(classpath, declaration.asJcDeclaration(jcClass), true)
                }
            }
        }


    override val superType: JcClassType? by lazy(LazyThreadSafetyMode.NONE) {
        val superClass = jcClass.superClass ?: return@lazy null
        resolutionImpl?.let {
            val newSubstitutor = superSubstitutor(superClass, it.superClass)
            JcClassTypeImpl(superClass, outerType, newSubstitutor, nullable, annotations)
        } ?: superClass.toType()
    }

    override val interfaces: List<JcClassType> by lazy(LazyThreadSafetyMode.NONE) {
        jcClass.interfaces.map { iface ->
            val ifaceType = resolutionImpl?.interfaceType?.firstOrNull { it.isReferencesClass(iface.name) }
            if (ifaceType != null) {
                val newSubstitutor = superSubstitutor(iface, ifaceType)
                JcClassTypeImpl(iface, null, newSubstitutor, nullable, annotations)
            } else {
                iface.toType()
            }
        }
    }

    override val innerTypes: List<JcClassType> by lazy(LazyThreadSafetyMode.NONE) {
        jcClass.innerClasses.map {
            val outerMethod = it.outerMethod
            val outerClass = it.outerClass

            val innerParameters = (
                    outerMethod?.allVisibleTypeParameters() ?: outerClass?.allVisibleTypeParameters()
                    )?.values?.toList().orEmpty()
            val innerSubstitutor = when {
                it.isStatic -> JcSubstitutor.empty.newScope(innerParameters)
                else -> substitutor.newScope(innerParameters)
            }
            JcClassTypeImpl(it, this, innerSubstitutor, true, annotations)
        }
    }

    override val declaredMethods by lazy(LazyThreadSafetyMode.NONE) {
        typedMethods(true, fromSuperTypes = false, jcClass.packageName)
    }

    override val methods by lazy(LazyThreadSafetyMode.NONE) {
        //let's calculate visible methods from super types
        typedMethods(true, fromSuperTypes = true, jcClass.packageName)
    }

    override val declaredFields by lazy(LazyThreadSafetyMode.NONE) {
        typedFields(true, fromSuperTypes = false, jcClass.packageName)
    }

    override val fields by lazy(LazyThreadSafetyMode.NONE) {
        typedFields(true, fromSuperTypes = true, jcClass.packageName)
    }

    override fun copyWithNullability(nullability: Boolean?) = JcClassTypeImpl(jcClass, outerType, substitutor, nullability, annotations.toMutableList())

    override fun copyWithAnnotations(annotations: List<JcAnnotation>): JcType =
        JcClassTypeImpl(jcClass, outerType, substitutor, nullable, annotations)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcClassTypeImpl

        if (nullable != other.nullable) return false
        if (typeName != other.typeName) return false

        return true
    }

    override fun hashCode(): Int {
        val result = nullable.hashCode()
        return 31 * result + typeName.hashCode()
    }

    private fun typedMethods(
        allMethods: Boolean,
        fromSuperTypes: Boolean,
        packageName: String
    ): List<JcTypedMethod> {
        val classPackageName = jcClass.packageName
        val methodSet = if (allMethods) {
            jcClass.declaredMethods
        } else {
            jcClass.declaredMethods.filter { !it.isConstructor && (it.isPublic || it.isProtected || (it.isPackagePrivate && packageName == classPackageName)) }
        }
        val declaredMethods: List<JcTypedMethod> = methodSet.map {
            JcTypedMethodImpl(this@JcClassTypeImpl, it, substitutor)
        }

        if (!fromSuperTypes) {
            return declaredMethods
        }
        val result = declaredMethods.toSortedSet(UnsafeHierarchyTypedMethodComparator)
        result.addAll(
            (superType as? JcClassTypeImpl)?.typedMethods(false, fromSuperTypes = true, packageName).orEmpty()
        )
        result.addAll(
            interfaces.flatMap {
                (it as? JcClassTypeImpl)?.typedMethods(false, fromSuperTypes = true, packageName).orEmpty()
            }
        )
        return result.toList()
    }

    private fun typedFields(all: Boolean, fromSuperTypes: Boolean, packageName: String): List<JcTypedField> {
        val classPackageName = jcClass.packageName

        val fieldSet = if (all) {
            jcClass.declaredFields
        } else {
            jcClass.declaredFields.filter { it.isPublic || it.isProtected || (it.isPackagePrivate && packageName == classPackageName) }
        }
        val directSet = fieldSet.map {
            JcTypedFieldImpl(this@JcClassTypeImpl, it, substitutor)
        }
        if (!fromSuperTypes) {
            return directSet
        }
        val result = directSet.toSortedSet<JcTypedField>(UnsafeHierarchyTypedFieldComparator)
        val superTypesToCheck = (listOf(superType) + interfaces).mapNotNull { it as? JcClassTypeImpl }

        result.addAll(
            superTypesToCheck.flatMap {
                it.typedFields(
                    false,
                    fromSuperTypes = true,
                    classPackageName
                )
            }
        )
        return result.toList()
    }


    private fun superSubstitutor(superClass: JcClassOrInterface, superType: JvmType): JcSubstitutor {
        val superParameters = superClass.directTypeParameters()
        val substitutions = (superType as? JvmParameterizedType)?.parameterTypes
        if (substitutions == null || superParameters.size != substitutions.size) {
            return JcSubstitutor.empty
        }
        return substitutor.fork(superParameters.mapIndexed { index, declaration -> declaration to substitutions[index] }
            .toMap())

    }

}

fun JvmType.isReferencesClass(name: String): Boolean {
    return when (val type = this) {
        is JvmClassRefType -> type.name == name
        is JvmParameterizedType -> type.name == name
        is JvmParameterizedType.JvmNestedType -> type.name == name
        else -> false
    }
}

// call with SAFE. comparator works only on methods from one hierarchy
private object UnsafeHierarchyTypedMethodComparator : Comparator<JcTypedMethod> {

    override fun compare(o1: JcTypedMethod, o2: JcTypedMethod): Int {
        return when (o1.name) {
            o2.name -> o1.method.description.compareTo(o2.method.description)
            else -> o1.name.compareTo(o2.name)
        }
    }
}

// call with SAFE. comparator works only on methods from one hierarchy
private object UnsafeHierarchyTypedFieldComparator : Comparator<JcTypedField> {

    override fun compare(o1: JcTypedField, o2: JcTypedField): Int {
        return o1.name.compareTo(o2.name)
    }
}