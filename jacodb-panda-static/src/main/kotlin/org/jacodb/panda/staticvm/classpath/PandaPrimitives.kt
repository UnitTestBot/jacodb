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

enum class PandaPrimitiveType(
    // override val project: PandaProject,
    override val typeName: String,
) : PandaSingleType {
    VOID("void"),
    NULL("null"),
    BOOL("u1"),
    BYTE("i8"),
    UBYTE("u8"),
    SHORT("i16"),
    USHORT("u16"),
    INT("i32"),
    UINT("u32"),
    LONG("i64"),
    ULONG("u64"),
    FLOAT("f32"),
    DOUBLE("f64");

    override fun toString(): String = typeName

    override val nullable: Boolean?
        get() = false
}

object PandaPrimitives {
    fun findPrimitiveOrNull(name: String): PandaPrimitiveType? =
        enumValues<PandaPrimitiveType>().find { it.typeName == name }

    fun find(name: String) = requireNotNull(findPrimitiveOrNull(name)) {
        "Not found primitive $name"
    }
}
