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

package org.jacodb.impl.cfg.util

import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.ext.jcdbName
import org.jacodb.api.jvm.ext.jvmName
import org.jacodb.impl.types.TypeNameImpl

internal val NULL = "null".typeName()
internal const val OBJECT_CLASS = "Ljava.lang.Object;"
internal const val STRING_CLASS = "Ljava.lang.String;"
internal const val THROWABLE_CLASS = "Ljava.lang.Throwable;"
internal const val CLASS_CLASS = "Ljava.lang.Class;"
internal const val METHOD_HANDLE_CLASS = "Ljava.lang.invoke.MethodHandle;"
internal const val METHOD_HANDLES_CLASS = "Ljava.lang.invoke.MethodHandles;"
internal const val METHOD_HANDLES_LOOKUP_CLASS = "Ljava.lang.invoke.MethodHandles\$Lookup;"
internal const val METHOD_TYPE_CLASS = "Ljava.lang.invoke.MethodType;"
internal const val LAMBDA_METAFACTORY_CLASS = "Ljava.lang.invoke.LambdaMetafactory;"
internal val TOP = "TOP".typeName()
internal val UNINIT_THIS = "UNINIT_THIS".typeName()

internal val TypeName.jvmTypeName get() = typeName.jvmName()
internal val TypeName.jvmClassName get() = jvmTypeName.removePrefix("L").removeSuffix(";")


val TypeName.internalDesc: String
    get() = when {
        isPrimitive -> jvmTypeName
        isArray -> {
            val element = elementType()
            when {
                element.isClass -> "[${element.jvmTypeName}"
                else -> "[${element.internalDesc}"
            }
        }

        else -> this.jvmClassName
    }

val TypeName.isPrimitive get() = PredefinedPrimitives.matches(typeName)
val TypeName.isArray get() = typeName.endsWith("[]")
val TypeName.isClass get() = !isPrimitive && !isArray

internal val TypeName.isDWord get() = typeName == PredefinedPrimitives.Long || typeName == PredefinedPrimitives.Double

internal fun String.typeName(): TypeName = TypeNameImpl(this.jcdbName())
fun TypeName.asArray(dimensions: Int = 1) = "$typeName${"[]".repeat(dimensions)}".typeName()
internal fun TypeName.elementType() = elementTypeOrNull() ?: this

internal fun TypeName.elementTypeOrNull() = when {
    this == NULL -> NULL
    typeName.endsWith("[]") -> typeName.removeSuffix("[]").typeName()
    else -> null
}

fun TypeName.baseElementType(): Pair<TypeName, Int> {
    var current: TypeName? = this
    var dim = -1
    var next: TypeName? = current
    do {
        current = next
        next = current!!.elementTypeOrNull()
        dim++
    } while (next != null)
    check(dim >= 0)
    return Pair(current!!, dim)
}

val lambdaMetaFactory: TypeName  = LAMBDA_METAFACTORY_CLASS.typeName()
val lambdaMetaFactoryMethodName: String = "metafactory"
