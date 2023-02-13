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

package org.jacodb.impl.storage

import org.jacodb.api.JCDBSymbolsInterner
import org.jacodb.api.RegisteredLocation
import org.jacodb.impl.storage.jooq.tables.references.ANNOTATIONS
import org.jacodb.impl.storage.jooq.tables.references.ANNOTATIONVALUES
import org.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.jacodb.impl.storage.jooq.tables.references.CLASSHIERARCHIES
import org.jacodb.impl.storage.jooq.tables.references.CLASSINNERCLASSES
import org.jacodb.impl.storage.jooq.tables.references.FIELDS
import org.jacodb.impl.storage.jooq.tables.references.METHODPARAMETERS
import org.jacodb.impl.storage.jooq.tables.references.METHODS
import org.jacodb.impl.storage.jooq.tables.references.OUTERCLASSES
import org.jacodb.impl.storage.jooq.tables.references.SYMBOLS
import org.jacodb.impl.types.AnnotationInfo
import org.jacodb.impl.types.AnnotationValue
import org.jacodb.impl.types.AnnotationValueList
import org.jacodb.impl.types.ClassInfo
import org.jacodb.impl.types.ClassRef
import org.jacodb.impl.types.EnumRef
import org.jacodb.impl.types.FieldInfo
import org.jacodb.impl.types.MethodInfo
import org.jacodb.impl.types.ParameterInfo
import org.jacodb.impl.types.PrimitiveValue
import org.jooq.DSLContext
import org.jooq.TableField
import java.io.Closeable
import java.sql.Connection
import java.sql.Types
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class PersistenceService(private val persistence: AbstractJcDatabasePersistenceImpl) {

    private val classIdGen = AtomicLong()
    private val symbolsIdGen = AtomicLong()
    private val methodIdGen = AtomicLong()
    private val fieldIdGen = AtomicLong()
    private val methodParamIdGen = AtomicLong()
    private val annotationIdGen = AtomicLong()
    private val annotationValueIdGen = AtomicLong()
    private val outerClassIdGen = AtomicLong()

    fun setup() {
        persistence.read {
            classIdGen.set(CLASSES.ID.maxId ?: 0)
            symbolsIdGen.set(SYMBOLS.ID.maxId ?: 0)
            methodIdGen.set(METHODS.ID.maxId ?: 0)
            fieldIdGen.set(FIELDS.ID.maxId ?: 0)
            methodParamIdGen.set(METHODPARAMETERS.ID.maxId ?: 0)
            annotationIdGen.set(ANNOTATIONS.ID.maxId ?: 0)
            annotationValueIdGen.set(ANNOTATIONVALUES.ID.maxId ?: 0)
            outerClassIdGen.set(OUTERCLASSES.ID.maxId ?: 0)
        }
    }

    fun persist(location: RegisteredLocation, classes: List<ClassInfo>) = synchronized(this) {
        val symbolInterner = persistence.symbolInterner
        val classCollector = ClassCollector(classIdGen)
        val annotationCollector = AnnotationCollector(annotationIdGen, annotationValueIdGen, symbolInterner)
        val fieldCollector = FieldCollector(fieldIdGen, annotationCollector)
        val classRefCollector = ClassRefCollector()
        val paramsCollector = MethodParamsCollector()
        val methodsCollector = MethodsCollector(methodIdGen, annotationCollector, paramsCollector)
        val names = HashSet<String>()
        classes.forEach {
            names.add(it.name.substringBeforeLast('.'))
            names.add(it.name)
            it.superClass?.let {
                names.add(it)
            }
            names.addAll(it.interfaces)
            names.addAll(it.innerClasses)
            names.addAll(listOfNotNull(it.outerClass?.name, it.outerMethod))
            names.addAll(it.methods.map { it.name })
            names.addAll(it.methods.map { it.returnClass })
            names.addAll(it.methods.flatMap { it.parameters })
            names.addAll(it.fields.map { it.name })
            names.addAll(it.fields.map { it.type })
            it.outerClass?.className?.let { names.add(it) }
            it.annotations.extractAllSymbolsTo(names)
            it.methods.forEach {
                it.annotations.extractAllSymbolsTo(names)
                it.parametersInfo.forEach { it.annotations.extractAllSymbolsTo(names) }
            }
            it.fields.forEach { it.annotations.extractAllSymbolsTo(names) }
        }
        names.forEach {
            symbolInterner.findOrNew(it)
        }
        persistence.write { jooq ->
            jooq.withoutAutoCommit { conn ->
                symbolInterner.flush(conn)
                val locationId = location.id
                classes.forEach { classCollector.collect(it) }
                classCollector.classes.entries.forEach { (classInfo, storedClassId) ->
                    if (classInfo.interfaces.isNotEmpty()) {
                        classInfo.interfaces.forEach {
                            classRefCollector.collectParent(storedClassId, it, isClass = false)
                        }
                    }
                    classRefCollector.collectParent(storedClassId, classInfo.superClass, isClass = true)
                    if (classInfo.innerClasses.isNotEmpty()) {
                        classInfo.innerClasses.forEach {
                            classRefCollector.collectInnerClass(storedClassId, it)
                        }
                    }
                    classInfo.methods.forEach {
                        methodsCollector.collect(storedClassId, it)
                    }
                    classInfo.fields.forEach {
                        fieldCollector.collect(storedClassId, it)
                    }
                }

                conn.insertElements(CLASSES, classCollector.classes.entries) {
                    val (classInfo, id) = it
                    val packageName = classInfo.name.substringBeforeLast('.')
                    val pack = packageName.findCachedSymbol()
                    setLong(1, id)
                    setInt(2, classInfo.access)
                    setLong(3, classInfo.name.findCachedSymbol())
                    setString(4, classInfo.signature)
                    setBytes(5, classInfo.bytecode)
                    setLong(6, locationId)
                    setLong(7, pack)
                    setNull(8, Types.BIGINT)
                    setNull(9, Types.BIGINT)
                    annotationCollector.collect(classInfo.annotations, id, RefKind.CLASS)
                }
                conn.insertElements(METHODS, methodsCollector.methods) {
                    val (classId, methodId, method) = it
                    setLong(1, methodId)
                    setInt(2, method.access)
                    setLong(3, method.name.findCachedSymbol())
                    setString(4, method.signature)
                    setString(5, method.desc)
                    setLong(6, method.returnClass.findCachedSymbol())
                    setLong(7, classId)
                    annotationCollector.collect(method.annotations, methodId, RefKind.METHOD)
                }

                conn.insertElements(METHODPARAMETERS, paramsCollector.params) {
                    val (methodId, param) = it
                    val paramId = methodParamIdGen.incrementAndGet()
                    setLong(1, paramId)
                    setInt(2, param.access)
                    setInt(3, param.index)
                    setString(4, param.name)
                    setLong(5, param.type.findCachedSymbol())
                    setLong(6, methodId)
                    annotationCollector.collect(param.annotations, paramId, RefKind.PARAM)
                }

                conn.insertElements(
                    CLASSINNERCLASSES,
                    classRefCollector.innerClasses,
                    autoIncrementId = true
                ) { (classId, innerClass) ->
                    setLong(1, classId)
                    setLong(2, innerClass.findCachedSymbol())
                }
                conn.insertElements(
                    CLASSHIERARCHIES,
                    classRefCollector.superClasses,
                    autoIncrementId = true
                ) { superClass ->
                    setLong(1, superClass.first)
                    setLong(2, superClass.second.findCachedSymbol())
                    setBoolean(3, superClass.third)
                }

                conn.insertElements(FIELDS, fieldCollector.fields) { (classId, fieldId, fieldInfo) ->
                    setLong(1, fieldId)
                    setInt(2, fieldInfo.access)
                    setLong(3, fieldInfo.name.findCachedSymbol())
                    setString(4, fieldInfo.signature)
                    setLong(5, fieldInfo.type.findCachedSymbol())
                    setLong(6, classId)
                }

                conn.insertElements(ANNOTATIONS, annotationCollector.collected) { annotation ->
                    setLong(1, annotation.id)
                    setLong(2, annotation.info.className.findCachedSymbol())
                    setBoolean(3, annotation.info.visible)
                    setNullableLong(4, annotation.info.typeRef?.toLong())
                    setString(5, annotation.info.typePath)
                    setNullableLong(6, annotation.parentId)

                    setNullableLong(7, annotation.refId.takeIf { annotation.refKind == RefKind.CLASS })
                    setNullableLong(8, annotation.refId.takeIf { annotation.refKind == RefKind.METHOD })
                    setNullableLong(9, annotation.refId.takeIf { annotation.refKind == RefKind.FIELD })
                    setNullableLong(10, annotation.refId.takeIf { annotation.refKind == RefKind.PARAM })
                }

                conn.insertElements(ANNOTATIONVALUES, annotationCollector.collectedValues) { value ->
                    setLong(1, value.id)
                    setLong(2, value.annotationId)
                    setString(3, value.name)
                    setNullableLong(4, value.refAnnotationId)
                    if (value.primitiveValueType != null) {
                        setInt(5, value.primitiveValueType.ordinal)
                        setString(6, value.primitiveValue?.replace("\u0000", ""))
                    } else {
                        setNull(5, Types.INTEGER)
                        setNull(6, Types.VARCHAR)
                    }
                    setNullableLong(7, value.classSymbolId)
                    setNullableLong(8, value.enumSymbolId)
                }

                conn.insertElements(OUTERCLASSES, classes.filter { it.outerClass != null }) { classInfo ->
                    val outerClass = classInfo.outerClass!!
                    val outerClassId = outerClass.className.findCachedSymbol()
                    setLong(1, outerClassIdGen.incrementAndGet())
                    setLong(2, outerClassId)
                    setString(3, outerClass.name)
                    setString(4, classInfo.outerMethod)
                    setString(5, classInfo.outerMethodDesc)
                }
            }
        }
    }

    fun findSymbolId(symbol: String): Long? {
        return persistence.symbolInterner.findOrNew(symbol)
    }

    fun findSymbolName(symbolId: Long): String {
        return persistence.read { jooq ->
            jooq.select(SYMBOLS.NAME).from(SYMBOLS)
                .where(SYMBOLS.ID.eq(symbolId)).fetchOne(SYMBOLS.NAME)!!
        }
    }

    private fun String.findCachedSymbol(): Long {
        return persistence.symbolInterner.findOrNew(this)
    }

    private val TableField<*, Long?>.maxId: Long?
        get() {
            return maxId(persistence.jooq)
        }
}

internal enum class RefKind {
    CLASS, FIELD, METHOD, PARAM,
}

private data class AnnotationItem(
    val id: Long,
    val parentId: Long?,
    val refId: Long,
    val refKind: RefKind,
    val info: AnnotationInfo
)

private data class AnnotationValueItem(
    val id: Long,
    val annotationId: Long,

    val name: String,

    val classSymbolId: Long? = null,
    val refAnnotationId: Long? = null,
    val enumSymbolId: Long? = null,

    val primitiveValue: String? = null,
    val primitiveValueType: AnnotationValueKind? = null,
)

private class AnnotationCollector(
    val annotationIdGen: AtomicLong,
    val annotationValueIdGen: AtomicLong,
    val symbolInterner: JCDBSymbolsInterner
) {
    val collected = ArrayList<AnnotationItem>()
    val collectedValues = ArrayList<AnnotationValueItem>()

    fun collect(annotations: List<AnnotationInfo>, refId: Long, kind: RefKind) {
        annotations.forEach {
            collect(it, refId, kind)
        }
    }

    fun collect(info: AnnotationInfo, refId: Long, kind: RefKind, parentId: Long? = null): Long {
        val id = annotationIdGen.incrementAndGet()
        val parent = AnnotationItem(id = id, refId = refId, info = info, refKind = kind, parentId = parentId)
        collected.add(parent)
        info.values.forEach {
            collectValue(it, parent)
        }
        return id
    }


    fun collectValue(nameValue: Pair<String, AnnotationValue>, parent: AnnotationItem) {
        val (name, value) = nameValue
        val valueId = annotationValueIdGen.incrementAndGet()
        when (value) {
            is AnnotationInfo -> {
                val refId = collect(value, parent.refId, parent.refKind, parent.id)
                collectedValues.add(
                    AnnotationValueItem(
                        id = valueId,
                        name = name,
                        refAnnotationId = refId,
                        annotationId = parent.id,
                        enumSymbolId = null,
                    )
                )
            }

            is ClassRef -> collectedValues.add(
                AnnotationValueItem(
                    id = valueId,
                    name = name,
                    classSymbolId = symbolInterner.findOrNew(value.className),
                    annotationId = parent.id,
                    enumSymbolId = null,
                )
            )

            is EnumRef -> collectedValues.add(
                AnnotationValueItem(
                    id = valueId,
                    name = name,
                    classSymbolId = symbolInterner.findOrNew(value.className),
                    enumSymbolId = symbolInterner.findOrNew(value.enumName),
                    annotationId = parent.id,
                )
            )

            is PrimitiveValue -> collectedValues.add(
                AnnotationValueItem(
                    id = valueId,
                    name = name,
                    annotationId = parent.id,
                    primitiveValue = AnnotationValueKind.serialize(value.value),
                    primitiveValueType = value.dataType
                )
            )

            is AnnotationValueList -> {
                value.annotations.forEach {
                    collectValue(name to it, parent)
                }
            }
        }
    }
}

fun List<AnnotationInfo>.extractAllSymbolsTo(result: HashSet<String>) {
    forEach { it.extractAllSymbolsTo(result) }
}

fun AnnotationInfo.extractAllSymbolsTo(result: HashSet<String>) {
    result.add(className)
    values.forEach {
        it.second.extractSymbolsTo(result)
    }
}

fun AnnotationValue.extractSymbolsTo(result: HashSet<String>) {
    when (this) {
        is AnnotationInfo -> extractAllSymbolsTo(result)
        is ClassRef -> result.add(className)
        is EnumRef -> {
            result.add(enumName)
            result.add(className)
        }

        is AnnotationValueList -> annotations.forEach { it.extractSymbolsTo(result) }
        else -> {}
    }
}

private class FieldCollector(private val fieldIdGen: AtomicLong, private val annotationCollector: AnnotationCollector) {

    val fields = ArrayList<Triple<Long, Long, FieldInfo>>()

    fun collect(classId: Long, fieldInfo: FieldInfo) {
        val fieldId = fieldIdGen.incrementAndGet()
        fields.add(Triple(classId, fieldId, fieldInfo))
        annotationCollector.collect(fieldInfo.annotations, fieldId, RefKind.FIELD)
    }
}

private class ClassRefCollector {

    val superClasses = ArrayList<Triple<Long, String, Boolean>>()
    val innerClasses = ArrayList<Pair<Long, String>>()

    fun collectParent(classId: Long, superClass: String?, isClass: Boolean = true) {
        if (superClass != null && superClass != "java.lang.Object") {
            superClasses += Triple(classId, superClass, isClass)
        }
    }

    fun collectInnerClass(classId: Long, innerClass: String) {
        innerClasses.add(classId to innerClass)
    }
}

private class MethodParamsCollector {

    val params = ArrayList<Pair<Long, ParameterInfo>>()

    fun collect(methodId: Long, param: ParameterInfo) {
        params.add(methodId to param)
    }

}

private class ClassCollector(private val classIdGen: AtomicLong) {

    val classes = HashMap<ClassInfo, Long>()

    fun collect(classInfo: ClassInfo) {
        val id = classIdGen.incrementAndGet()
        classes[classInfo] = id
    }

}

private class MethodsCollector(
    private val methodIdGen: AtomicLong,
    private val annotationCollector: AnnotationCollector,
    private val paramsCollector: MethodParamsCollector
) {

    val methods = ArrayList<Triple<Long, Long, MethodInfo>>()

    fun collect(classId: Long, method: MethodInfo) {
        val methodId = methodIdGen.incrementAndGet()
        methods.add(Triple(classId, methodId, method))
        method.parametersInfo.forEach {
            paramsCollector.collect(methodId, it)
        }
        annotationCollector.collect(method.annotations, methodId, RefKind.METHOD)
    }

}

class JCDBSymbolsInternerImpl(override val jooq: DSLContext) : JCDBSymbolsInterner, Closeable {
    private val symbolsIdGen = AtomicLong()
    private val symbolsCache = ConcurrentHashMap<String, Long>()
    private val newElements = ConcurrentHashMap<String, Long>()

    fun setup() {
        jooq.selectFrom(SYMBOLS).fetch().forEach {
            val (id, name) = it
            if (name != null && id != null)
                symbolsCache[name] = id
        }
        symbolsIdGen.set(SYMBOLS.ID.maxId(jooq) ?: 0)
    }

    override fun findOrNew(symbol: String): Long {
        return symbolsCache.computeIfAbsent(symbol) {
            symbolsIdGen.incrementAndGet().also {
                newElements[symbol] = it
            }
        }
    }

    override fun flush(conn: Connection) {
        val entries = newElements.entries.toList()
        conn.insertElements(SYMBOLS, entries, onConflict = "ON CONFLICT(id) DO NOTHING") { (value, id) ->
            setLong(1, id)
            setString(2, value)
        }
        entries.forEach {
            newElements.remove(it.key)
        }
    }

    override fun close() {
        symbolsCache.clear()
        newElements.clear()
    }
}