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

import org.jacodb.api.*
import org.jacodb.api.cfg.JcInstLocation
import org.jacodb.api.cfg.JcRawCallExpr
import org.jacodb.api.cfg.TypedMethodRef
import org.jacodb.api.ext.*
import org.jacodb.impl.softLazy
import org.jacodb.impl.weakLazy

data class TypedMethodRefImpl(
    val type: JcClassType,
    override val name: String,
    val argTypes: List<TypeName>,
    val returnType: TypeName
) : TypedMethodRef {

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

    override val method: JcTypedMethod by weakLazy {
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
    return TypedMethodRefImpl(this, expr)
}

fun JcType.methodRef(expr: JcRawCallExpr): TypedMethodRef {
    return TypedMethodRefImpl(this, expr)
}

fun JcTypedMethod.methodRef(): TypedMethodRef {
    return TypedMethodRefImpl(
        enclosingType as JcClassType,
        method.name,
        method.parameters.map { it.type },
        method.returnType
    )
}


class JcMethodRefImpl(method: JcMethod): JcMethodRef {

    private val classpath = method.enclosingClass.classpath
    private val className: String = method.enclosingClass.name
    private val name: String = method.name
    private val description: String = method.description

    override val method get() = classpath.findClass(className).findMethodOrNull(name, description)!!

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcMethodRefImpl

        if (className != other.className) return false
        if (name != other.name) return false
        return description == other.description
    }

    override fun hashCode(): Int {
        var result = className.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + description.hashCode()
        return result
    }

}

class JcInstLocationImpl(
    val methodRef: JcMethodRef,
    override val index: Int,
    override val lineNumber: Int
) : JcInstLocation {

    override val method: JcMethod by softLazy {
        methodRef.method
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcInstLocationImpl

        if (index != other.index) return false
        return methodRef == other.methodRef
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + methodRef.hashCode()
        return result
    }


}