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

package org.jacodb.impl.cfg

import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.TypeName
import org.jacodb.api.cfg.JcInstLocation
import org.jacodb.api.cfg.JcRawCallExpr
import org.jacodb.api.cfg.JcRawSpecialCallExpr
import org.jacodb.api.cfg.JcRawStaticCallExpr
import org.jacodb.api.cfg.TypedMethodRef
import org.jacodb.api.ext.findMethodOrNull
import org.jacodb.api.ext.hasAnnotation
import org.jacodb.api.ext.jvmName
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.packageName
import org.jacodb.impl.softLazy
import org.jacodb.impl.weakLazy

interface MethodSignatureRef : TypedMethodRef {
    val type: JcClassType
    val argTypes: List<TypeName>

    fun findDeclaredMethod(filter: (JcTypedMethod) -> Boolean): JcTypedMethod? {
        return type.findDeclaredMethod(filter)
    }

    fun findDeclaredMethod(): JcTypedMethod? {
        return type.findDeclaredMethod { true }
    }

    fun JcClassType.findDeclaredMethod(filter: (JcTypedMethod) -> Boolean): JcTypedMethod? {
        val types = argTypes.joinToString { it.typeName }
        return this.declaredMethods.firstOrNull { it.name == name && filter(it) && it.method.parameters.joinToString { it.type.typeName } == types }
    }

    val methodNotFoundMessage: String
        get() {
            return buildString {
                append("Can't find method '")
                append(type.typeName)
                append("#")
                append(name)
                append("(")
                argTypes.forEach {
                    append(it.typeName)
                    append(",")
                }
                append(")")
            }
        }
}

data class TypedStaticMethodRefImpl(
    override val type: JcClassType,
    override val name: String,
    override val argTypes: List<TypeName>,
    val returnType: TypeName
) : MethodSignatureRef {

    constructor(classpath: JcClasspath, raw: JcRawStaticCallExpr) : this(
        classpath.findTypeOrNull(raw.declaringClass.typeName) as JcClassType,
        raw.methodName,
        raw.argumentTypes,
        raw.returnType
    )

    override val method: JcTypedMethod by weakLazy {
        findDeclaredMethod { it.isStatic } ?: type.superType?.let {
            TypedStaticMethodRefImpl(it, name, argTypes, returnType).method
        } ?: throw IllegalStateException(methodNotFoundMessage)
    }
}

data class TypedSpecialMethodRefImpl(
    override val type: JcClassType,
    override val name: String,
    override val argTypes: List<TypeName>,
    val returnType: TypeName
) : MethodSignatureRef {

    constructor(classpath: JcClasspath, raw: JcRawSpecialCallExpr) : this(
        classpath.findTypeOrNull(raw.declaringClass.typeName) as JcClassType,
        raw.methodName,
        raw.argumentTypes,
        raw.returnType
    )

    override val method: JcTypedMethod by weakLazy {
        findDeclaredMethod() ?: type.superType?.let {
            TypedSpecialMethodRefImpl(it, name, argTypes, returnType).method
        } ?: throw IllegalStateException(methodNotFoundMessage)
    }

}


data class TypedMethodRefImpl(
    override val type: JcClassType,
    override val name: String,
    override val argTypes: List<TypeName>,
    val returnType: TypeName
) : MethodSignatureRef {

    constructor(classpath: JcClasspath, raw: JcRawCallExpr) : this(
        classpath.findTypeOrNull(raw.declaringClass.typeName) as JcClassType,
        raw.methodName,
        raw.argumentTypes,
        raw.returnType
    )

    constructor(type: JcType, raw: JcRawCallExpr) : this(
        (type as? JcClassType) ?: type.classpath.objectType,
        raw.methodName,
        raw.argumentTypes,
        raw.returnType
    )

    override val method: JcTypedMethod by softLazy {
        type.getMethod(name, argTypes, returnType)
    }

    private fun JcClassType.getMethod(name: String, argTypes: List<TypeName>, returnType: TypeName): JcTypedMethod {
        val sb = buildString {
            append("(")
            argTypes.forEach {
                append(it.typeName.jvmName())
            }
            append(")")
            append(returnType.typeName.jvmName())
        }
        var methodOrNull = findMethodOrNull(name, sb)
        if (methodOrNull == null && jcClass.packageName == "java.lang.invoke") {
            methodOrNull = findMethodOrNull {
                val method = it.method
                method.name == name && method.hasAnnotation("java.lang.invoke.MethodHandle\$PolymorphicSignature")
            } // weak consumption. may fail
        }
        return methodOrNull ?: error("Could not find a method with correct signature $typeName#$name$sb")
    }
}

fun JcClasspath.methodRef(expr: JcRawCallExpr): TypedMethodRef {
    return when (expr) {
        is JcRawStaticCallExpr -> TypedStaticMethodRefImpl(this, expr)
        is JcRawSpecialCallExpr -> TypedSpecialMethodRefImpl(this, expr)
        else -> TypedMethodRefImpl(this, expr)
    }
}

fun JcTypedMethod.methodRef(): TypedMethodRef {
    return TypedMethodRefImpl(
        enclosingType as JcClassType,
        method.name,
        method.parameters.map { it.type },
        method.returnType
    )
}

class JcInstLocationImpl(
    override val method: JcMethod,
    override val index: Int,
    override val lineNumber: Int
) : JcInstLocation {

    override fun toString(): String {
        return "${method.enclosingClass.name}#${method.name}:$lineNumber"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcInstLocationImpl

        if (index != other.index) return false
        return method == other.method
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + method.hashCode()
        return result
    }


}