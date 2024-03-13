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

package org.jacodb.panda.staticvm.classpath

data class AccessFlags(val flags: Int) {
    val isPublic: Boolean
        get() = flags and 0x0001 > 0
    val isPrivate: Boolean
        get() = flags and 0x0002 > 0
    val isProtected: Boolean
        get() = flags and 0x0004 > 0
    val isPackagePrivate: Boolean
        get() = false
    val isStatic: Boolean
        get() = flags and 0x0008 > 0
    val isFinal: Boolean
        get() = flags and 0x0010 > 0
    val isInterface: Boolean
        get() = flags and 0x0200 > 0
    val isAbstract: Boolean
        get() = flags and 0x0400 > 0
    val isSynthetic: Boolean
        get() = flags and 0x1000 > 0
}
