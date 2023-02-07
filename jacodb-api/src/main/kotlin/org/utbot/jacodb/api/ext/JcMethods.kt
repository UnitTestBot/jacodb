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

@file:JvmName("JcMethods")

package org.utbot.jacodb.api.ext

import org.objectweb.asm.Opcodes
import org.utbot.jacodb.api.JcMethod


/**
 * is method has `native` modifier
 */
val JcMethod.isNative: Boolean
    get() {
        return access and Opcodes.ACC_NATIVE != 0
    }

/**
 * is item has `synchronized` modifier
 */
val JcMethod.isSynchronized: Boolean
    get() {
        return access and Opcodes.ACC_SYNCHRONIZED != 0
    }

/**
 * return true if method is constructor
 */
val JcMethod.isConstructor: Boolean
    get() {
        return name == "<init>"
    }

val JcMethod.isClassInitializer: Boolean
    get() {
        return name == "<clinit>"
    }


/**
 * is method has `strictfp` modifier
 */
val JcMethod.isStrict: Boolean
    get() {
        return access and Opcodes.ACC_STRICT != 0
    }

val JcMethod.jvmSignature: String
    get() {
        return name + description
    }

val JcMethod.jcdbSignature: String
    get() {
        val params = parameters.joinToString(";") { it.type.typeName } + (";".takeIf { parameters.isNotEmpty() } ?: "")
        return "$name($params)${returnType.typeName};"
    }

val JcMethod.humanReadableSignature: String
    get() {
        val params = parameters.joinToString(",") { it.type.typeName }
        return "${returnType.typeName} $name($params)"
    }

@get:JvmName("hasBody")
val JcMethod.hasBody: Boolean
    get() {
        return !isNative && !isAbstract && body().instructions.first != null
    }
