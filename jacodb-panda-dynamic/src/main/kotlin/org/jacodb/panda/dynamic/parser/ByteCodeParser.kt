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

import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.experimental.and

class ByteCodeParser(
    val byteCodeBuffer: ByteBuffer
) {

    fun parse() {
        Header.parse(0, buffer).printHeader()
    }

    private lateinit var parsedFile: ABC
    val buffer: ByteBuffer = byteCodeBuffer

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

        constructor() : this(
            Header(),
            ClassIndex(),
            IndexSection(),
            emptyList(),
            emptyList(),
            MethodStringLiteralRegionIndex(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList()
        )

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

        fun printHeader() {
            println("Magic: ${magic.joinToString("") { it.toString() }}")
            println("Checksum: ${checksum.joinToString("") { it.toString() }}")
            println("Version: ${version.joinToString("") { it.toString() }}")
            println("File Size: $fileSize")
            println("Foreign Off: $foreignOff")
            println("Foreign Size: $foreignSize")
            println("Number of Classes: $numClasses")
            println("Class Index Off: $classIdxOff")
            println("Number of Line Number Programs: $numberOfLineNumberPrograms")
            println("Line Number Program Index Offset: $lineNumberProgramIndexOffset")
            println("Number of Literal Arrays: $numLiteralArrays")
            println("Literal Array Index Off: $literalArrayIdxOff")
            println("Number of Index Regions: $numIndexRegions")
            println("Index Section Off: $indexSectionOff")
        }

        companion object {

            fun parse(offset: Int, buffer: ByteBuffer): Header {
                return readHeader(buffer)
            }
        }

        constructor() : this(
            emptyList(),
            emptyList(),
            emptyList(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0
        )
    }

    data class ClassIndex(
        val offsets: List<Int>
    ) {
        companion object {

            fun parse(offset: Int): ClassIndex {
                return ClassIndex()
            }
        }

        constructor() : this(emptyList())
    }

    data class IndexSection(
        val indexHeader: IndexHeader
    ) {
        companion object {

            fun parse(offset: Int): IndexSection {
                return IndexSection()
            }
        }

        constructor() : this(IndexHeader())
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

        constructor() : this(
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
        )
    }


    data class ClassRegionIndex(
        val fieldType: List<FieldType>
    ) {

        companion object {

            fun parse(offset: Int): ClassRegionIndex {
                return ClassRegionIndex()
            }
        }

        constructor() : this(emptyList())
    }

    data class FieldType(
        val type: Int,
    ) {
        companion object {

            fun parse(offset: Int): FieldType {
                return FieldType()
            }
        }

        constructor() : this(0)
    }

    data class ProtoRegionIndex(
        val proto: List<Proto>
    ) {
        companion object {

            fun parse(offset: Int): ProtoRegionIndex {
                return ProtoRegionIndex()
            }
        }

        constructor() : this(emptyList())
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

        constructor() : this(0, 0)
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

        constructor() : this(0, 0, 0)
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

        constructor() : this(0, 0,  0, 0, FieldData())
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

        constructor() : this(0, 0)
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

        constructor() : this(emptyList(), emptyList())
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

        constructor() : this(0, "")
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

        constructor() : this(0, 0, 0, 0, MethodData(), 0)
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

        constructor() : this(0, 0, 0, 0, 0, 0, 0, 0)
    }

    data class Code(
        val numVregs: Byte,
        val numArgs: Byte,
        val codeSize: Byte,
        val triesSize: Byte,
        val insts: String,
        val tryBlocks: List<Int>
    ) {
        companion object {

            fun parse(offset: Int): Code {
                return Code()
            }
        }

        constructor() : this(0, 0, 0, 0, "", emptyList())
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

        constructor() : this(0, 0, 0)
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

        constructor() : this(0, 0)
    }

//    fun getMethodCodeByName(methodName: String): Code {
//        parsedFile.method.find { m -> byteBuffer.parseString(m.nameOff) == methodName }.
//    }
//

    companion object {
        fun ByteBuffer.getBytes(size: Int) = ByteArray(size).also { get(it) }

        fun ByteBuffer.getBytesByOffset(offset: Int, size: Int) = ByteArray(size).also { position(offset); get(it) }

        fun ByteBuffer.getBytesList(size: Int) = List(size) { get() }

        fun ByteBuffer.getShorts(size: Int) = ShortArray(size).also { for (i in 0 until size) it[i] = short }

        fun ByteBuffer.getInts(size: Int) = IntArray(size).also { for (i in 0 until size) it[i] = int }

        fun ByteBuffer.getByte() = get()

        val Int.b: Byte
            get() = toByte()

        val Int.ub: UByte
            get() = toUByte()

        fun ByteBuffer.getULEB128(): ULong {
            var result = 0UL
            var shift = 0
            while (true) {
                val byte = get().toUByte()
                result = result.or((byte and 0x7f.ub).toULong() shl shift)
                if (byte and 0x80.ub == 0.ub)
                    break
                shift += 7
            }
            return result
        }

        fun ByteBuffer.getSLEB128(): Long {
            var result = 0L
            var shift = 0
            var signBits = -1L

            while (true) {
                val byte = get()
                result = result.or((byte and 0x7f).toLong() shl shift)
                signBits = signBits shl 7
                if (byte and 0x80.b == 0.b)
                    break
                shift += 7
            }

            if ((signBits shr 1) and result != 0L) {
                result = result or signBits
            }

            return result
        }

        fun ByteBuffer.getString(): String =
            getBytes(getULEB128().toInt() shr 1).toString(Charset.forName("UTF-8")).also { get() }

        fun ByteBuffer.getShortyGroup(): List<Byte> = getShort().let { bytes ->
            listOf(0, 4, 8, 12).map { bytes.toInt().shr(it).toByte() and 0x0f.b }.dropLastWhile { it == 0.b }
        }

        fun readHeader(buffer: ByteBuffer) = buffer.run {
            Header(
                getBytesList(8),
                getBytesList(4),
                getBytesList(4),
                getInt(),
                getInt(),
                getInt(),
                getInt(),
                getInt(),
                getInt(),
                getInt(),
                getInt(),
                getInt(),
                getInt(),
                getInt()
            )
        }

    }

}
