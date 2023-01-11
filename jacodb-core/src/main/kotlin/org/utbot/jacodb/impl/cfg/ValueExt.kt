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

package org.utbot.jacodb.impl.cfg

import org.utbot.jacodb.api.PredefinedPrimitives
import org.utbot.jacodb.api.TypeName
import org.utbot.jacodb.api.cfg.JcRawNullConstant
import org.utbot.jacodb.api.cfg.JcRawStringConstant
import org.utbot.jacodb.impl.cfg.util.NULL
import org.utbot.jacodb.impl.cfg.util.STRING_CLASS
import org.utbot.jacodb.impl.cfg.util.typeName

fun JcRawNull() = JcRawNullConstant(NULL)
fun JcRawBool(value: Boolean) = org.utbot.jacodb.api.cfg.JcRawBool(value, PredefinedPrimitives.Boolean.typeName())
fun JcRawByte(value: Byte) = org.utbot.jacodb.api.cfg.JcRawByte(value, PredefinedPrimitives.Byte.typeName())
fun JcRawShort(value: Short) = org.utbot.jacodb.api.cfg.JcRawShort(value, PredefinedPrimitives.Short.typeName())
fun JcRawChar(value: Char) = org.utbot.jacodb.api.cfg.JcRawChar(value, PredefinedPrimitives.Char.typeName())
fun JcRawInt(value: Int) = org.utbot.jacodb.api.cfg.JcRawInt(value, PredefinedPrimitives.Int.typeName())
fun JcRawLong(value: Long) = org.utbot.jacodb.api.cfg.JcRawLong(value, PredefinedPrimitives.Long.typeName())
fun JcRawFloat(value: Float) = org.utbot.jacodb.api.cfg.JcRawFloat(value, PredefinedPrimitives.Float.typeName())
fun JcRawDouble(value: Double) = org.utbot.jacodb.api.cfg.JcRawDouble(value, PredefinedPrimitives.Double.typeName())

fun JcRawZero(typeName: TypeName) = when (typeName.typeName) {
    PredefinedPrimitives.Boolean -> JcRawBool(false)
    PredefinedPrimitives.Byte -> JcRawByte(0)
    PredefinedPrimitives.Char -> JcRawChar(0.toChar())
    PredefinedPrimitives.Short -> JcRawShort(0)
    PredefinedPrimitives.Int -> JcRawInt(0)
    PredefinedPrimitives.Long -> JcRawLong(0)
    PredefinedPrimitives.Float -> JcRawFloat(0.0f)
    PredefinedPrimitives.Double -> JcRawDouble(0.0)
    else -> error("Unknown primitive type: $typeName")
}

fun JcRawNumber(number: Number) = when (number) {
    is Int -> JcRawInt(number)
    is Float -> JcRawFloat(number)
    is Long -> JcRawLong(number)
    is Double -> JcRawDouble(number)
    else -> error("Unknown number: $number")
}

fun JcRawString(value: String) = JcRawStringConstant(value, STRING_CLASS.typeName())
