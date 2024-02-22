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

@file:JvmName("JcValues")

package org.jacodb.impl.cfg

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcByte
import org.jacodb.api.jvm.cfg.JcDouble
import org.jacodb.api.jvm.cfg.JcFloat
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLong
import org.jacodb.api.jvm.cfg.JcRawBool
import org.jacodb.api.jvm.cfg.JcRawByte
import org.jacodb.api.jvm.cfg.JcRawChar
import org.jacodb.api.jvm.cfg.JcRawDouble
import org.jacodb.api.jvm.cfg.JcRawFloat
import org.jacodb.api.jvm.cfg.JcRawInt
import org.jacodb.api.jvm.cfg.JcRawLong
import org.jacodb.api.jvm.cfg.JcRawNullConstant
import org.jacodb.api.jvm.cfg.JcRawShort
import org.jacodb.api.jvm.cfg.JcRawStringConstant
import org.jacodb.api.jvm.cfg.JcShort
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.short
import org.jacodb.impl.cfg.util.NULL
import org.jacodb.impl.cfg.util.STRING_CLASS
import org.jacodb.impl.cfg.util.typeName

@JvmName("rawNull")
fun JcRawNull() = JcRawNullConstant(NULL)

@JvmName("rawBool")
fun JcRawBool(value: Boolean) =
    JcRawBool(value, PredefinedPrimitives.Boolean.typeName())

@JvmName("rawByte")
fun JcRawByte(value: Byte) =
    JcRawByte(value, PredefinedPrimitives.Byte.typeName())

@JvmName("rawShort")
fun JcRawShort(value: Short) =
    JcRawShort(value, PredefinedPrimitives.Short.typeName())

@JvmName("rawChar")
fun JcRawChar(value: Char) =
    JcRawChar(value, PredefinedPrimitives.Char.typeName())

@JvmName("rawInt")
fun JcRawInt(value: Int) =
    JcRawInt(value, PredefinedPrimitives.Int.typeName())

@JvmName("rawLong")
fun JcRawLong(value: Long) =
    JcRawLong(value, PredefinedPrimitives.Long.typeName())

@JvmName("rawFloat")
fun JcRawFloat(value: Float) =
    JcRawFloat(value, PredefinedPrimitives.Float.typeName())

@JvmName("rawDouble")
fun JcRawDouble(value: Double) =
    JcRawDouble(value, PredefinedPrimitives.Double.typeName())

@JvmName("rawZero")
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

@JvmName("rawNumber")
fun JcRawNumber(number: Number) = when (number) {
    is Int -> JcRawInt(number)
    is Float -> JcRawFloat(number)
    is Long -> JcRawLong(number)
    is Double -> JcRawDouble(number)
    else -> error("Unknown number: $number")
}

@JvmName("rawString")
fun JcRawString(value: String) =
    JcRawStringConstant(value, STRING_CLASS.typeName())

fun JcClasspath.int(value: Int): JcInt = JcInt(value, int)
fun JcClasspath.byte(value: Byte): JcByte = JcByte(value, byte)
fun JcClasspath.short(value: Short): JcShort = JcShort(value, short)
fun JcClasspath.long(value: Long): JcLong = JcLong(value, long)
fun JcClasspath.boolean(value: Boolean): JcBool = JcBool(value, boolean)
fun JcClasspath.double(value: Double): JcDouble = JcDouble(value, double)
fun JcClasspath.float(value: Float): JcFloat = JcFloat(value, float)
