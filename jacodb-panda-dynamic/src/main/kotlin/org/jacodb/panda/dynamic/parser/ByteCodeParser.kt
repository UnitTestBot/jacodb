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

    interface Parsable {
        fun parse()
    }

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
    ) : Parsable {
        override fun parse() {
            header.parse()
            classIndex.parse()
            indexSection.parse()
            foreignField.forEach { it.parse() }
            field.forEach { it.parse() }
            methodStringLiteralRegionIndex.parse()
            stringData.forEach { it.parse() }
            method.forEach { it.parse() }
            methodIndexData.forEach { it.parse() }
            taggedValue.forEach { it.parse() }
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
    ) : Parsable {
        override fun parse() {
            println("Header: $this")
        }
    }

    data class ClassIndex(
        val offsets: List<Int>
    ) : Parsable {
        override fun parse() {
            println("ClassIndex: $this")
        }
    }

    data class IndexSection(
        val indexHeader: IndexHeader
    ) : Parsable {
        override fun parse() {
            indexHeader.parse()
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
    ) : Parsable {
        private lateinit var classRegionIndex: ClassRegionIndex
        override fun parse() {
            println("IndexHeader: $this")
        }
    }


    data class ClassRegionIndex(
        val fieldType: List<FieldType>
    ) : Parsable {
        override fun parse() {
            println("ClassRegionIndex: $this")
        }
    }

    data class FieldType(
        val type: Int,
    ) : Parsable {
        override fun parse() {
            println("FieldType: $this")
        }
    }

    data class ProtoRegionIndex(
        val proto: List<Proto>
    ) : Parsable {
        override fun parse() {
            println("ProtoRegionIndex: $this")
        }
    }

    data class Proto(
        val shorty: Int,
        val referenceType: Int
    ) : Parsable {
        override fun parse() {
            println("Proto: $this")
        }
    }

    data class ForeignField(
        val classIdx: Short,
        val typeIdx: Short,
        val nameOff: Int
    ) : Parsable {
        override fun parse() {
            println("ForeignField: $this")
        }
    }

    data class Field(
        val classIdx: Short,
        val typeIdx: Short,
        val nameOff: Int,
        val accessFlags: Byte,
        val fieldData: FieldData
    ) : Parsable {
        override fun parse() {
            println("Field: $this")
        }
    }

    data class FieldData(
        val tagValue: Byte,
        val data: Byte
    ) : Parsable {
        override fun parse() {
            println("FieldData: $this")
        }
    }

    data class MethodStringLiteralRegionIndex(
        val string: List<Int>,
        val method: List<Int>
    ) : Parsable {
        override fun parse() {
            println("MethodStringLiteralRegionIndex: $this")
        }
    }

    data class StringData(
        val utf16Length: Byte,
        val data: String
    ) : Parsable {
        override fun parse() {
            println("StringData: $this")
        }
    }

    data class Method(
        val classIdx: Short,
        val protoIdx: Short,
        val nameOff: Int,
        val accessFlag: Short,
        val methodData: MethodData,
        val end: Byte
    ) : Parsable {
        override fun parse() {
            println("Method: $this")
        }
    }

    data class MethodData(
        val flag: Byte,
        val code: Code?,
        val number1: Byte,
        val number2: Byte,
        val number3: Byte,
        val number4: Int,
        val number5: Byte,
        val number6: Int
    ) : Parsable {
        override fun parse() {
            println("MethodData: $this")
            code?.parse()
        }
    }

    data class Code(
        val numVregs: Byte,
        val numArgs: Byte,
        val codeSize: Byte,
        val triesSize: Byte,
        val insts: String
    ) : Parsable {
        override fun parse() {
            println("Code: $this")
        }
    }

    data class MethodIndexData(
        val headerIdx: Short,
        val functionKind: Byte,
        val accessFlags: Byte
    ) : Parsable {
        override fun parse() {
            println("MethodIndexData: $this")
        }
    }

    data class TaggedValue(
        val tagValue: Byte,
        val data: Short
    ) : Parsable {
        override fun parse() {
            println("TaggedValue: $this")
        }
    }

}