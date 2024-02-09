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

@file:JvmName("JcFields")

package org.jacodb.api.ext

import org.jacodb.api.JcField
import org.objectweb.asm.Opcodes

/**
 * is item has `volatile` modifier
 */
val JcField.isVolatile: Boolean
    get() {
        return access and Opcodes.ACC_VOLATILE != 0
    }

/**
 * is field has `transient` modifier
 */
val JcField.isTransient: Boolean
    get() {
        return access and Opcodes.ACC_TRANSIENT != 0
    }

val JcField.isEnum: Boolean
    get() {
        return access and Opcodes.ACC_ENUM != 0
    }
