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
    ADD2(0x0a.ub, 2),
    ASYNCGENERATORREJECT(0x2e.ub, 2, PandaBytecodePrefix.DEPRECATED),
    CALLARG0_IMM_8(0x29.ub, 1),
    CALLARG0_V_8(0x0b.ub, 1, prefix = PandaBytecodePrefix.DEPRECATED),
    CALLARG1(0x2a.ub, 2),
    CALLARGS2(0x2b.ub, 3),
    CALLINIT(0x06.ub, 2, prefix = PandaBytecodePrefix.CALLRUNTIME),
    CALLTHIS0(0x2d.ub, 2),
    CALLTHIS1(0x2e.ub, 3),
    CALLTHIS2(0x2f.ub, 4),
    CALLTHIS3(0x30.ub, 5),
    CALLTHISRANGE_IMM1_8_IMM2_8_V_8(0x31.ub, 3),
    CALLTHISRANGE_IMM_16_V_8(0x05.ub, 3, prefix = PandaBytecodePrefix.WIDE),
    CALLTHISRANGE_IMM_16_V_8_DEPRECATED(0x11.ub, 3, prefix = PandaBytecodePrefix.DEPRECATED),
    CLOSEITERATOR_IMM_16_V_8(0xac.ub, 3),
    CLOSEITERATOR_IMM_8_V_8(0x68.ub, 2),
    COPYRESTARGS_IMM_16(0x0b.ub, 2, prefix = PandaBytecodePrefix.WIDE),
    COPYRESTARGS_IMM_8(0xcf.ub, 1),
    CREATEARRAYWITHBUFFER_IMM_16(0x03.ub, 2, prefix = PandaBytecodePrefix.DEPRECATED),
    CREATEARRAYWITHBUFFER_IMM_16_ID_16(0x81.ub, 4),
    CREATEARRAYWITHBUFFER_IMM_8_ID_16(0x06.ub, 3),
    CREATEEMPTYARRAY_IMM_16(0x80.ub, 2),
    CREATEEMPTYARRAY_IMM_8(0x05.ub, 1),
    CREATEOBJECTWITHBUFFER_IMM_16_ID_16(0x82.ub, 4),
    CREATEOBJECTWITHBUFFER_IMM_8_ID_16(0x07.ub, 3),
    CREATEOBJECTWITHEXCLUDEDKEYS(0x00.ub, 4, PandaBytecodePrefix.WIDE),
    DEFINECLASSWITHBUFFER_IMM1_16_ID1_16_ID2_16_IMM2_16_V_8(0x75.ub, 9),
    DEFINECLASSWITHBUFFER_IMM1_8_ID1_16_ID2_16_IMM2_16_V_8(0x35.ub, 8),
    DEFINEFIELDBYNAME(0xdb.ub, 4),
    DEFINEFUNC_IMM1_16_ID_16_IMM2_8(0x74.ub, 5),
    DEFINEFUNC_IMM1_8_ID_16_IMM2_8(0x33.ub, 4),
    DEFINEMETHOD_IMM1_16_ID_16_IMM2_8(0xbe.ub, 5),
    DEFINEMETHOD_IMM1_8_ID_16_IMM2_8(0x34.ub, 4),
    IFSUPERNOTCORRECTCALL_IMM_16(0x08.ub, 2, prefix = PandaBytecodePrefix.THROW),
    IFSUPERNOTCORRECTCALL_IMM_8(0x07.ub, 1, prefix = PandaBytecodePrefix.THROW),
    INC(0x21.ub, 1),
    ISFALSE(0x24.ub),
    ISTRUE(0x23.ub),
    JEQZ_IMM_16(0x50.ub, 2),
    JEQZ_IMM_32(0x9a.ub, 4),
    JEQZ_IMM_8(0x4f.ub, 1),
    JMP_IMM_16(0x4e.ub, 2),
    JMP_IMM_32(0x98.ub, 4),
    JMP_IMM_8(0x4d.ub, 1),
    LDA(0x60.ub, 1),
    LDAI(0x62.ub, 4),
    LDA_STR(0x3e.ub, 2),
    LDEXTERNALMODULEVAR_IMM_16(0x7e.ub, 1, prefix = PandaBytecodePrefix.WIDE),
    LDEXTERNALMODULEVAR_IMM_8(0x7e.ub, 1),
    LDFALSE(0x03.ub),
    LDGLOBALVAR(0x41.ub, 4),
    LDHOLE(0x70.ub),
    LDHOMEOBJECT(0x2b.ub, prefix = PandaBytecodePrefix.DEPRECATED),
    LDLEXENV(0x00.ub, prefix = PandaBytecodePrefix.DEPRECATED),
    LDLEXVAR_IMM1_4_IMM2_4(0x3c.ub, 1),
    LDLEXVAR_IMM1_8_IMM2_8(0x8a.ub, 2),
    LDLOCALMODULEVAR_IMM_16(0x10.ub, 2, prefix = PandaBytecodePrefix.WIDE),
    LDLOCALMODULEVAR_IMM_8(0x7d.ub, 1),
    LDNULL(0x01.ub),
    LDOBJBYNAME_IMM_16_ID_16(0x90.ub, 4),
    LDOBJBYNAME_IMM_8_ID_16(0x42.ub, 3),
    LDOBJBYVALUE_IMM_16_V_8(0x85.ub, 3),
    LDOBJBYVALUE_IMM_8_V_8(0x37.ub, 2),
    LDTRUE(0x02.ub),
    LDUNDEFINED(0x00.ub),
    LESS(0x11.ub, 2),
    LESSEQ(0x12.ub, 2),
    MOV_V1_16_V2_16(0x8f.ub, 4),
    MOV_V1_4_V2_4(0x44.ub, 1),
    MOV_V1_8_V2_8(0x45.ub, 2),
    NEWLEXENV_IMM_16(0x02.ub, 2, prefix = PandaBytecodePrefix.WIDE),
    NEWLEXENV_IMM_8(0x09.ub, 1),
    NEWOBJRANGE_IMM1_16_IMM2_8_V_8(0x83.ub, 4),
    NEWOBJRANGE_IMM1_8_IMM2_8_V_8(0x08.ub, 3),
    NOTEQ(0x10.ub, 2),
    NOTIFYCONCURRENTRESULT(0x00.ub, prefix = PandaBytecodePrefix.CALLRUNTIME),
    POPLEXENV(0x69.ub),
    RETURN(0x64.ub),
    RETURNUNDEFINED(0x65.ub),
    STA(0x61.ub, 1),
    STARRAYSPREAD(0xc6.ub, 2),
    STLEXVAR_IMM1_16_IMM2_16_V8(0x22.ub, 5, prefix = PandaBytecodePrefix.DEPRECATED),
    STLEXVAR_IMM1_4_IMM2_4(0x3d.ub, 1),
    STLEXVAR_IMM1_4_IMM2_4_V8(0x20.ub, 2, prefix = PandaBytecodePrefix.DEPRECATED),
    STLEXVAR_IMM1_8_IMM2_8(0x8b.ub, 2),
    STLEXVAR_IMM1_8_IMM2_8_V8(0x21.ub, 3, prefix = PandaBytecodePrefix.DEPRECATED),
    STMODULEVAR_IMM_16(0x0f.ub, 2, prefix = PandaBytecodePrefix.WIDE),
    STMODULEVAR_IMM_8(0x7c.ub, 1),
    STOBJBYNAME_IMM_16_ID_16_V_8(0x91.ub, 5),
    STOBJBYNAME_IMM_8_ID_16_V_8(0x43.ub, 4),
    STOWNBYNAME_IMM_16_ID_16_V_8(0xcc.ub, 5),
    STOWNBYNAME_IMM_8_ID_16_V_8(0x7a.ub, 4),
    STRICTEQ(0x28.ub, 2),
    STRICTNOTEQ(0x27.ub, 2),
    SUPERCALLSPREAD(0xb9.ub, 2),
    THROW(0x00.ub, prefix = PandaBytecodePrefix.THROW),
    TONUMERIC(0x1e.ub, 1),
    TRYLDGLOBALBYNAME_IMM_16_ID_16(0x8c.ub, 4),
    TRYLDGLOBALBYNAME_IMM_8_ID_16(0x3f.ub, 3),
    TYPEOF_IMM_16(0x84.ub, 2),
    TYPEOF_IMM_8(0x1c.ub, 1),
    UNDEFINEDIFHOLEWITHNAME(0x09.ub, 2, prefix = PandaBytecodePrefix.THROW);

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
