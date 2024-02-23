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

package org.jacodb.panda.dynamic.parser

import org.jacodb.panda.dynamic.parser.ByteCodeParser.Companion.ub

enum class PandaBytecodePrefix(val prefix: UByte) {
    THROW(0xfe.ub),
    WIDE(0xfd.ub),
    DEPRECATED(0xfc.ub),
    CALLRUNTIME(0xfb.ub);

    override fun toString(): String {
        return "PandaBytecodePrefix(name = ${this.name}, prefix=0x${prefix.toString(radix = 16)})"
    }

    companion object {
        fun from(findValue: UByte): PandaBytecodePrefix? =
            PandaBytecodePrefix
                .values()
                .firstOrNull { it.prefix == findValue }
    }
}

enum class PandaBytecode(val opcode: UByte, val operands: Int = 0, val prefix: PandaBytecodePrefix? = null) {
    LDA(0x60.ub, 1),
    LDGLOBALVAR(0x41.ub, 4),
    STA(0x61.ub, 1),
    CLOSEITERATOR_IMM_8_V_8(0x68.ub, 2),
    CLOSEITERATOR_IMM_16_V_8(0xac.ub, 3),
    TYPEOF_IMM_8(0x1c.ub, 1),
    TYPEOF_IMM_16(0x84.ub, 2),
    NOTEQ(0x10.ub, 2),
    JEQZ_IMM_8(0x4f.ub, 1),
    JEQZ_IMM_16(0x50.ub, 2),
    JEQZ_IMM_32(0x9a.ub, 4),
    TRYLDGLOBALBYNAME_IMM_8_ID_16(0x3f.ub, 3),
    TRYLDGLOBALBYNAME_IMM_16_ID_16(0x8c.ub, 4),
    LDA_STR(0x3e.ub, 2),
    MOV_V1_4_V2_4(0x44.ub, 1),
    MOV_V1_8_V2_8(0x45.ub, 2),
    MOV_V1_16_V2_16(0x8f.ub, 4),
    NEWOBJRANGE_IMM1_8_IMM2_8_V_8(0x08.ub, 3),
    NEWOBJRANGE_IMM1_16_IMM2_8_V_8(0x83.ub, 4),
    ADD2(0x0a.ub, 2),
    RETURN(0x64.ub),
    RETURNUNDEFINED(0x65.ub),
    LDAI(0x62.ub, 4),
    LDHOMEOBJECT(0x2b.ub, prefix = PandaBytecodePrefix.DEPRECATED),
    CALLARGS2(0x2b.ub, 3),
    JMP_IMM_8(0x4d.ub, 1),
    JMP_IMM_16(0x4e.ub, 2),
    JMP_IMM_32(0x98.ub, 4),
    LDOBJBYNAME_IMM_8_ID_16(0x42.ub, 3),
    LDOBJBYNAME_IMM_16_ID_16(0x90.ub, 4),
    CALLTHIS1(0x2e.ub, 3),
    ASYNCGENERATORREJECT(0x2e.ub, 2, PandaBytecodePrefix.DEPRECATED),
    THROW(0x00.ub, prefix = PandaBytecodePrefix.THROW),
    NOTIFYCONCURRENTRESULT(0x00.ub, prefix = PandaBytecodePrefix.CALLRUNTIME),
    CREATEOBJECTWITHEXCLUDEDKEYS(0x00.ub, 4, PandaBytecodePrefix.WIDE),
    LDLEXENV(0x00.ub, prefix = PandaBytecodePrefix.DEPRECATED),
    LDUNDEFINED(0x00.ub)
    ;

    override fun toString(): String {
        return "PandaBytecode(name = ${this.name}, opcode=0x${opcode.toString(radix = 16)}, operands=$operands, prefix=$prefix)"
    }

    companion object {

        private val accSetList: List<PandaBytecode>
            get() = listOf(
                LDA,
                LDGLOBALVAR,
                TRYLDGLOBALBYNAME_IMM_8_ID_16,
                TRYLDGLOBALBYNAME_IMM_16_ID_16,
                LDOBJBYNAME_IMM_8_ID_16,
                LDOBJBYNAME_IMM_16_ID_16
            )

        private val regSetList: List<PandaBytecode>
            get() = listOf(
                STA
            )

        fun getBytecode(findValue: UByte, prefix: PandaBytecodePrefix? = null): PandaBytecode =
            PandaBytecode
                .values()
                .first {
                    (it.opcode == findValue) && (it.prefix == prefix)
                }

        fun getBytecodeOrNull(findValue: UByte, prefix: PandaBytecodePrefix? = null): PandaBytecode? =
            PandaBytecode
                .values()
                .firstOrNull {
                    (it.opcode == findValue) && (it.prefix == prefix)
                }
    }

    fun isAccSet() = accSetList.contains(this)

    fun isRegSet() = regSetList.contains(this)

}
