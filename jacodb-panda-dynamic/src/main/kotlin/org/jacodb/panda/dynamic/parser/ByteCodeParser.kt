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

import kotlinx.serialization.descriptors.PrimitiveKind
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.LinkedList
import kotlin.experimental.and

class ByteCodeParser(
    private val byteCodeBuffer: ByteBuffer
) {

    fun parseABC(): ABC {
        val header = readHeader(byteCodeBuffer)
        val methodStringLiteralIndex = header.indexSection.indexHeader.methodStringLiteralIndex
        return ABC(
            header,
            header.classIndex,
            header.indexSection,
            emptyList(),
            emptyList(),
            methodStringLiteralIndex,
            methodStringLiteralIndex.methods.map { readMethod(it, byteCodeBuffer) },
            emptyList()
        )
    }

    private lateinit var parsedFile: ABC
    private var currentProtoIdxSize = 0
    private var currentClassIdxSize = 0

    inner class ABC(
        val header: Header,
        val classIndex: ClassIndex,
        val indexSection: IndexSection,
        val foreignField: List<ForeignField>,
        val field: List<Field>,
        val methodStringLiteralRegionIndex: MethodStringLiteralRegionIndex,
        val methods: List<Method>,
        val methodIndexData: List<MethodIndexData>,
    ) {

        constructor() : this(
            Header(),
            ClassIndex(),
            IndexSection(),
            emptyList(),
            emptyList(),
            MethodStringLiteralRegionIndex(),
            emptyList(),
            emptyList()
        )

    }

    inner class Header(
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

        val classIndex: ClassIndex = readClassIndex(numClasses, classIdxOff, byteCodeBuffer)

        val indexSection: IndexSection = readIndexSection(indexSectionOff, byteCodeBuffer)

        override fun toString(): String {
            return "Header(\n" +
                    "    magic                        = $magic,\n" +
                    "    checksum                     = $checksum,\n" +
                    "    version                      = $version,\n" +
                    "    fileSize                     = $fileSize,\n" +
                    "    foreignOff                   = $foreignOff,\n" +
                    "    foreignSize                  = $foreignSize,\n" +
                    "    numClasses                   = $numClasses,\n" +
                    "    classIdxOff                  = $classIdxOff,\n" +
                    "    numberOfLineNumberPrograms   = $numberOfLineNumberPrograms,\n" +
                    "    lineNumberProgramIndexOffset = $lineNumberProgramIndexOffset,\n" +
                    "    numLiteralArrays             = $numLiteralArrays,\n" +
                    "    literalArrayIdxOff           = $literalArrayIdxOff,\n" +
                    "    numIndexRegions              = $numIndexRegions,\n" +
                    "    indexSectionOff              = $indexSectionOff\n" +
                    ")"
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

    inner class ClassIndex(
        val offsets: List<Int>
    ) {

        constructor() : this(emptyList())

        override fun toString() = "ClassIndex(${offsets.joinToString(separator = ", ") { "0x" + it.toString(16) }})"
    }

    inner class IndexSection(
        val indexHeader: IndexHeader
    ) {

        constructor() : this(IndexHeader())
    }

    inner class IndexHeader(
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

        init {
            currentClassIdxSize = classIdxSize
            currentProtoIdxSize = protoIdxSize
        }

        val classRegionIndex: ClassRegionIndex = readClassRegionIndex(classIdxSize, classIdxOff, byteCodeBuffer)

        val methodStringLiteralIndex: MethodStringLiteralRegionIndex  = readMethodStringLiteralRegionIndex(
            methodStringLiteralIdxSize, methodStringLiteralIdxOff, byteCodeBuffer
        )

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


    inner class ClassRegionIndex(
        val fieldType: List<FieldType>
    ) {

        constructor() : this(emptyList())
    }

    inner class FieldType(
        val type: Int,
    ) {

        constructor() : this(0)
    }

    inner class ProtoRegionIndex(
        val proto: List<Proto>
    ) {

        constructor() : this(emptyList())
    }

    inner class Proto(
        val shorty: Int,
        val referenceType: Int
    ) {

        constructor() : this(0, 0)
    }

    inner class ForeignField(
        val classIdx: Short,
        val typeIdx: Short,
        val nameOff: Int
    ) {

        constructor() : this(0, 0, 0)
    }

    inner class Field(
        val classIdx: Short,
        val typeIdx: Short,
        val nameOff: Int,
        val accessFlags: Byte,
        val fieldData: FieldData
    ) {

        constructor() : this(0, 0,  0, 0, FieldData())
    }

    inner class FieldData(
        val tagValue: Byte,
        val data: Byte
    ) {

        constructor() : this(0, 0)
    }

    inner class MethodStringLiteralRegionIndex(
        val strings: List<Int>,
        val methods: List<Int>,
        val all: List<Int>
    ) {

        constructor() : this(emptyList(), emptyList(), emptyList())
    }

    inner class StringData(
        val utf16Length: Byte,
        val data: String
    ) {

        constructor() : this(0, "")
    }

    inner class Method(
        val classIdx: Short,
        val protoIdx: Short,
        val nameOff: Int,
        val accessFlag: Short,
        val methodData: MethodData,
    ) {

        val name: String = byteCodeBuffer.jumpTo(nameOff) { it.getString() }

        override fun toString(): String {
            return "Method(\n" +
                    "    classIdx = $classIdx,\n" +
                    "    protoIdx = $protoIdx,\n" +
                    "    nameOff = $nameOff,\n" +
                    "    accessFlag = $accessFlag,\n" +
                    "    methodData = $methodData\n" +
                    ")"
        }

        constructor() : this(0, 0, 0, 0, MethodData())
    }

     inner class MethodData(
        val tags: List<MethodTag>
     ) {
         override fun toString(): String {
             return "MethodData(\n" +
                     tags.joinToString(separator = "") { it.toString() } +
                     ")"
         }

        lateinit var code: Code

        constructor() : this(emptyList())
    }

    sealed interface TaggedValueName

    sealed interface MethodTagName

    inner class MethodTag(
        val tag: Byte,
        val payload: List<Byte>
    ) {
        override fun toString(): String {
            return "\t\tMethodTag(\n" +
                    "\t\t\ttag     = $tag,\n" +
                    "\t\t\tpayload = $payload\n" +
                    "\t\t)\n"
        }
    }

    inner class Code(
        val numVregs: Byte,
        val numArgs: Byte,
        val codeSize: Byte,
        val triesSize: Byte,
        val insts: Instruction,
        val tryBlocks: List<Int>
    ) {

        constructor() : this(0, 0, 0, 0, Instruction(), emptyList())

        fun getInstByOffset(offset: Int): Instruction {
            if (offset in insts.offset..(insts.offset + insts.operands.size)) return insts
            val instIterator = iterator()
            while (instIterator.hasNext()) {
                val next = instIterator.next()
                if (offset in next.offset..(next.offset + next.operands.size)) return next
            }

            throw IllegalArgumentException("no such bc offset")
        }


        /*
            CURRENTLY ON SUPPORTS RESOLVE FOR callarg BYTECODES

            Instructions that set acc:
            1. lda
            2. ldglobalvar
         */
        fun getAccValueByOffset(offset: Int): List<Byte> {
            val lastAccSet = findPrevious(offset) { PandaBytecode.from(it.opcode).isAccSet() } ?: return emptyList()

            return resolve(lastAccSet)
        }

        fun resolve(inst: Instruction): List<Byte> {
            when (PandaBytecode.from(inst.opcode)) {
                PandaBytecode.LDA -> {
                    val vReg = inst.operands.first()
                    val lastRegSet = findPrevious(inst.offset - 1) {
                        PandaBytecode.from(it.opcode).isRegSet() && it.operands.first() == vReg
                    } ?: throw IllegalStateException("can't resolve acc value for register")

                    return resolve(lastRegSet)
                }

                PandaBytecode.LDGLOBALVAR -> {
                    val stringId = inst.operands.drop(2).toByteBuffer().getInt()
                    val methodNameOffset = parsedFile.methodStringLiteralRegionIndex.all[stringId]
                    val name = byteCodeBuffer.jumpTo(methodNameOffset) { it.getString() }.map { it.code.toByte() }

                    return name
                }

                PandaBytecode.STA -> {
                    return getAccValueByOffset(inst.offset)
                }

                else -> {
                    throw IllegalStateException("opcode ${PandaBytecode.from(inst.opcode).name} not implemented")
                }
            }
        }

        fun findNext(startOffset: Int, filter: (Instruction) -> Boolean): Instruction? {
            val currentInst = getInstByOffset(startOffset)
            if (filter(currentInst)) return currentInst

            val iterator = iterator()
            while(iterator.hasNext()) {
                if (filter(iterator.next())) return currentInst
            }

            return null
        }

        fun findPrevious(startOffset: Int, filter: (Instruction) -> Boolean): Instruction? {
            val currentInst = getInstByOffset(startOffset)
            if (filter(currentInst)) return currentInst

            val iterator = iterator()
            while(iterator.hasPrevious()) {
                if (filter(iterator.previous())) return currentInst
            }

            return null
        }

        fun iterator(): ListIterator<Instruction> {
            return InstructionIterator(insts)
        }
    }

    inner class Instruction(
        val nextInst : Instruction?,
        val prevInst : Instruction?,
        val offset: Int,
        val opcode: Byte,
        val operands: List<Byte>
    ) {

        constructor() : this(null, null,0, 0, emptyList())
    }

    class InstructionIterator(start: Instruction) : ListIterator<Instruction> {

        private var currentInst = start
        private var index = 0

        override fun hasNext(): Boolean {
            return currentInst.nextInst != null
        }

        override fun hasPrevious(): Boolean {
            return currentInst.prevInst != null
        }

        override fun next(): Instruction {
            currentInst = currentInst.nextInst!!
            index++
            return currentInst
        }

        override fun nextIndex(): Int {
            return if (hasNext()) index + 1 else index
        }

        override fun previous(): Instruction {
            currentInst = currentInst.prevInst!!
            index--
            return currentInst
        }

        override fun previousIndex(): Int {
            return if (hasPrevious()) index - 1 else index
        }

    }

    inner class MethodIndexData(
        val headerIdx: Short,
        val functionKind: Byte,
        val accessFlags: Byte
    )  {

        constructor() : this(0, 0, 0)
    }

    inner class TaggedValue(
        val tagValue: Byte,
        val data: Short
    ) {

        constructor() : this(0, 0)
    }

    fun getMethodCodeByName(methodName: String): Code {
        return parsedFile.methods.find { m ->
            m.name == methodName
        }?.methodData?.code ?: throw IllegalStateException("no code")
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

    fun readClassIndex(size: Int, offset: Int, buffer: ByteBuffer) = buffer.jumpTo(offset) {
        ClassIndex(it.getInts(size).toList())
    }

    fun readIndexSection(offset: Int, buffer: ByteBuffer) = IndexSection(readIndexHeader(offset, buffer))

    fun readIndexHeader(offset: Int, buffer: ByteBuffer) = buffer.jumpTo(offset) {
        IndexHeader(
            it.getInt(),
            it.getInt(),
            it.getInt(),
            it.getInt(),
            it.getInt(),
            it.getInt(),
            it.getInt(),
            it.getInt(),
            it.getInt(),
            it.getInt()
        )
    }

    fun readClassRegionIndex(size: Int, offset: Int, buffer: ByteBuffer) = buffer.jumpTo(offset) { bb ->
        ClassRegionIndex(List(size) { readFieldType(bb) })
    }

    fun readFieldType(buffer: ByteBuffer) = buffer.run {
        FieldType(getInt())
    }

    fun readMethodStringLiteralRegionIndex(size: Int, offset: Int, buffer: ByteBuffer) = buffer.jumpTo(offset) { bb ->
        val stringList = mutableListOf<Int>()
        val methodList = mutableListOf<Int>()
        val all = mutableListOf<Int>()

        repeat(size) {
            val off = bb.getInt()
            all.add(off)
            tryReadMethod(off, bb)?.let { methodList.add(off) } ?: stringList.add(off)
        }

        MethodStringLiteralRegionIndex(stringList, methodList, all)
    }

    private fun tryReadMethod(offset: Int, buffer: ByteBuffer): Method? = buffer.jumpTo(offset) {
        try {
            readMethod(offset, buffer)
        } catch (e: IllegalStateException) {
            null
        }
    }

    fun readMethod(offset: Int, buffer: ByteBuffer) = buffer.jumpTo(offset) {
        val classIdx = it.getShort()
        val protoIdx = it.getShort()
        if (classIdx >= currentClassIdxSize || protoIdx >= currentProtoIdxSize)
            throw IllegalStateException("index overflow")

        Method(
            classIdx,
            protoIdx,
            it.getInt(),
            it.getShort(),
            readMethodData(it),
        )
    }

    fun readMethodTag(buffer: ByteBuffer) = buffer.run {
        val tag = getByte()
        // https://wiki.huawei.com/domains/1048/wiki/8/WIKI202305071123597?title=MethodTag
        val payloadSize = when (tag.toInt()) {
            0 -> 0
            1 -> 4
            2 -> 1
            5 -> 4
            6 -> 4
            else -> throw IllegalStateException("Unknown tag: $tag")
        }
        val payload = getBytes(payloadSize)
        MethodTag(tag, payload.toList())
    }

    fun readMethodData(buffer: ByteBuffer) = buffer.run {
        var methodTag = readMethodTag(this)
        val tags = mutableListOf(methodTag)
        while (methodTag.tag.toInt() != 0) {
            methodTag = readMethodTag(this)
            tags.add(methodTag)
        }
        MethodData(tags)
    }

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

        fun ByteBuffer.getString(size: Int): String =
            getBytes(size).toString(Charset.forName("UTF-8")).also { get() }

        fun ByteBuffer.getShortyGroup(): List<Byte> = getShort().let { bytes ->
            listOf(0, 4, 8, 12).map { bytes.toInt().shr(it).toByte() and 0x0f.b }.dropLastWhile { it == 0.b }
        }

        fun <T> ByteBuffer.jumpTo(offset: Int, action: (ByteBuffer) -> T): T {
            val prevPos = this.position()
            this.position(offset)
            return action(this).also { this@jumpTo.position(prevPos) }
        }

        fun List<Byte>.toByteBuffer() = ByteBuffer.wrap(this.toByteArray())

    }

}

enum class PandaBytecode(val opcode: Byte) {
    LDA(96),
    LDGLOBALVAR(65),
    STA(97);

    companion object {

        private val accSetList: List<PandaBytecode> get() = listOf(
            LDA, LDGLOBALVAR
        )

        private val regSetList: List<PandaBytecode> get() = listOf(
            STA
        )

        fun from(findValue: Byte): PandaBytecode = PandaBytecode.values().first { it.opcode == findValue }

    }

    fun isAccSet() = accSetList.contains(this)

    fun isRegSet() = regSetList.contains(this)

}
