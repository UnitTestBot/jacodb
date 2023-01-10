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

@file:JvmName("JcAccessibles")
package org.utbot.jacodb.api.ext

import org.objectweb.asm.Opcodes
import org.utbot.jacodb.api.JcAccessible

/**
 * is item has `public` modifier
 */
val JcAccessible.isPublic: Boolean
    get() {
        return access and Opcodes.ACC_PUBLIC != 0
    }

/**
 * is item has `private` modifier
 */
val JcAccessible.isPrivate: Boolean
    get() {
        return access and Opcodes.ACC_PRIVATE != 0
    }

/**
 * is item has `protected` modifier
 */
val JcAccessible.isProtected: Boolean
    get() {
        return access and Opcodes.ACC_PROTECTED != 0
    }

/**
 * is item has `package` modifier
 */
val JcAccessible.isPackagePrivate: Boolean
    get() {
        return !isPublic && !isProtected && !isPrivate
    }

/**
 * is item has `static` modifier
 */
val JcAccessible.isStatic: Boolean
    get() {
        return access and Opcodes.ACC_STATIC != 0
    }

/**
 * is item has `final` modifier
 */
val JcAccessible.isFinal: Boolean
    get() {
        return access and Opcodes.ACC_FINAL != 0
    }

/**
 * is item has `abstract` modifier
 */
val JcAccessible.isAbstract: Boolean
    get() {
        return access and Opcodes.ACC_ABSTRACT != 0
    }

val JcAccessible.isSynthetic: Boolean
    get() {
        return access and Opcodes.ACC_SYNTHETIC != 0
    }
