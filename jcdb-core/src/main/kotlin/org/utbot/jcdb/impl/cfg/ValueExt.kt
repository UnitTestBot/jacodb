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

package org.utbot.jcdb.impl.cfg

import org.utbot.jcdb.api.JcRawNullConstant
import org.utbot.jcdb.api.JcRawStringConstant
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.api.TypeName
import org.utbot.jcdb.impl.cfg.util.NULL
import org.utbot.jcdb.impl.cfg.util.STRING_CLASS
import org.utbot.jcdb.impl.cfg.util.typeName

fun JcRawNull() = JcRawNullConstant(NULL)
fun JcRawBool(value: Boolean) = org.utbot.jcdb.api.JcRawBool(value, PredefinedPrimitives.boolean.typeName())
fun JcRawByte(value: Byte) = org.utbot.jcdb.api.JcRawByte(value, PredefinedPrimitives.byte.typeName())
fun JcRawShort(value: Short) = org.utbot.jcdb.api.JcRawShort(value, PredefinedPrimitives.short.typeName())
fun JcRawChar(value: Char) = org.utbot.jcdb.api.JcRawChar(value, PredefinedPrimitives.char.typeName())
fun JcRawInt(value: Int) = org.utbot.jcdb.api.JcRawInt(value, PredefinedPrimitives.int.typeName())
fun JcRawLong(value: Long) = org.utbot.jcdb.api.JcRawLong(value, PredefinedPrimitives.long.typeName())
fun JcRawFloat(value: Float) = org.utbot.jcdb.api.JcRawFloat(value, PredefinedPrimitives.float.typeName())
fun JcRawDouble(value: Double) = org.utbot.jcdb.api.JcRawDouble(value, PredefinedPrimitives.double.typeName())

fun JcRawZero(typeName: TypeName) = when (typeName.typeName) {
    PredefinedPrimitives.boolean -> JcRawBool(false)
    PredefinedPrimitives.byte -> JcRawByte(0)
    PredefinedPrimitives.char -> JcRawChar(0.toChar())
    PredefinedPrimitives.short -> JcRawShort(0)
    PredefinedPrimitives.int -> JcRawInt(0)
    PredefinedPrimitives.long -> JcRawLong(0)
    PredefinedPrimitives.float -> JcRawFloat(0.0f)
    PredefinedPrimitives.double -> JcRawDouble(0.0)
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
