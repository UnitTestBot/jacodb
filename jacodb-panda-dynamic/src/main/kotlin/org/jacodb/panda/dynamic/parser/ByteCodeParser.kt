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

class ByteCodeParser(val filePath: String) {

    private val parsedFile: ABC
    private val byteBuffer: ByteArray

    data class ABC(
        val header: Header,
        val classIndex: ClassIndex,
        val indexSection: IndexSection,
        val foreignField: List<ForeignField>,
        val field: List<Field>,
        val methodStringLiteralRegionIndex: MethodStringLiteralRegionIndex,
        val stringData: List<StringData>,
        val method: List<Method>,
        val methodIndexData: List<MethodIndexData>,
        val taggedValue: List<TaggedValue>
    ) {
        companion object {

            fun parse(): ABC {
                return ABC()
            }
        }

    }

    data class Header(
        val magic: List<Byte>,
        val checksum: List<Byte>,
        val version: List<Byte>,
        val fileSize: Int,
        val foreignOff: Int,
        val foreignSize: Int,
        val numClasses: Int,
        val classIdxOff: Int,
        val numberOfLineNumberPrograms: Int,
        val lineNumberProgramIndexOffset: Int,
        val numLiteralArrays: Int,
        val literalArrayIdxOff: Int,
        val numIndexRegions: Int,
        val indexSectionOff: Int
    ) {
        companion object {

            fun parse(offset: Int): Header {
                return Header()
            }
        }
    }

    data class ClassIndex(
        val offsets: List<Int>
    ) {
        companion object {

            fun parse(offset: Int): ClassIndex {
                return ClassIndex()
            }
        }
    }

    data class IndexSection(
        val indexHeader: IndexHeader
    ) {
        companion object {

            fun parse(offset: Int): IndexSection {
                return IndexSection()
            }
        }
    }

    data class IndexHeader(
        val startOff: Int,
        val endOff: Int,
        val classIdxSize: Int,
        val classIdxOff: Int,
        val methodStringLiteralIdxSize: Int,
        val methodStringLiteralIdxOff: Int,
        val fieldIdxSize: Int,
        val fieldIdxOff: Int,
        val protoIdxSize: Int,
        val protoIdxOff: Int
    ) {

        private lateinit var classRegionIndex: ClassRegionIndex

        companion object {

            fun parse(offset: Int): IndexHeader {
                return IndexHeader()
            }
        }
    }


    data class ClassRegionIndex(
        val fieldType: List<FieldType>
    ) {

        companion object {

            fun parse(offset: Int): ClassRegionIndex {
                return ClassRegionIndex()
            }
        }
    }

    data class FieldType(
        val type: Int,
    ) {
        companion object {

            fun parse(offset: Int): FieldType {
                return FieldType()
            }
        }
    }

    data class ProtoRegionIndex(
        val proto: List<Proto>
    ) {
        companion object {

            fun parse(offset: Int): ProtoRegionIndex {
                return ProtoRegionIndex()
            }
        }
    }

    data class Proto(
        val shorty: Int,
        val referenceType: Int
    ) {
        companion object {

            fun parse(offset: Int): Proto {
                return Proto()
            }
        }
    }

    data class ForeignField(
        val classIdx: Short,
        val typeIdx: Short,
        val nameOff: Int
    ) {
        companion object {

            fun parse(offset: Int): ForeignField {
                return ForeignField()
            }
        }
    }

    data class Field(
        val classIdx: Short,
        val typeIdx: Short,
        val nameOff: Int,
        val accessFlags: Byte,
        val fieldData: FieldData
    ) {
        companion object {

            fun parse(offset: Int): Field {
                return Field()
            }
        }
    }

    data class FieldData(
        val tagValue: Byte,
        val data: Byte
    ) {
        companion object {

            fun parse(offset: Int): FieldData {
                return FieldData()
            }
        }
    }

    data class MethodStringLiteralRegionIndex(
        val string: List<Int>,
        val method: List<Int>
    ) {
        companion object {

            fun parse(offset: Int): MethodStringLiteralRegionIndex {
                return MethodStringLiteralRegionIndex()
            }
        }
    }

    data class StringData(
        val utf16Length: Byte,
        val data: String
    ) {
        companion object {

            fun parse(offset: Int): StringData {
                return StringData()
            }
        }
    }

    data class Method(
        val classIdx: Short,
        val protoIdx: Short,
        val nameOff: Int,
        val accessFlag: Short,
        val methodData: MethodData,
        val end: Byte
    ) {
        companion object {

            fun parse(offset: Int): Method {
                return Method()
            }
        }
    }

    data class MethodData(
        val flag: Byte,
        val codeOff: Int,
        val number1: Byte,
        val number2: Byte,
        val number3: Byte,
        val number4: Int,
        val number5: Byte,
        val number6: Int
    ) {

        private lateinit var code: Code

        companion object {

            fun parse(offset: Int): MethodData {
                return MethodData()
            }
        }
    }

    data class Code(
        val numVregs: Byte,
        val numArgs: Byte,
        val codeSize: Byte,
        val triesSize: Byte,
        val insts: String
    ) {
        companion object {

            fun parse(offset: Int): Code {
                return Code()
            }
        }
    }

    data class MethodIndexData(
        val headerIdx: Short,
        val functionKind: Byte,
        val accessFlags: Byte
    )  {
        companion object {

            fun parse(offset: Int): MethodIndexData {
                return MethodIndexData()
            }
        }
    }

    data class TaggedValue(
        val tagValue: Byte,
        val data: Short
    ) {
        companion object {

            fun parse(offset: Int): TaggedValue {
                return TaggedValue()
            }
        }
    }

    fun getMethodCodeByName(methodName: String): Code {
        parsedFile.method.find { m -> byteBuffer.parseString(m.nameOff) == methodName }.
    }

    private fun ByteArray.parseString(offset: Int): String {

    }

}
