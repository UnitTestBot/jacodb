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

/*
 * This file is generated by jOOQ.
 */
package org.jacodb.impl.storage.jooq.tables.records


import org.jacodb.impl.storage.jooq.tables.Classhierarchies
import org.jooq.Field
import org.jooq.Record1
import org.jooq.Record4
import org.jooq.Row4
import org.jooq.impl.UpdatableRecordImpl


/**
 * This class is generated by jOOQ.
 */
@Suppress("UNCHECKED_CAST")
open class ClasshierarchiesRecord() : UpdatableRecordImpl<ClasshierarchiesRecord>(Classhierarchies.CLASSHIERARCHIES), Record4<Long?, Long?, Long?, Boolean?> {

    var id: Long?
        set(value) = set(0, value)
        get() = get(0) as Long?

    var classId: Long?
        set(value) = set(1, value)
        get() = get(1) as Long?

    var superId: Long?
        set(value) = set(2, value)
        get() = get(2) as Long?

    var isClassRef: Boolean?
        set(value) = set(3, value)
        get() = get(3) as Boolean?

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    override fun key(): Record1<Long?> = super.key() as Record1<Long?>

    // -------------------------------------------------------------------------
    // Record4 type implementation
    // -------------------------------------------------------------------------

    override fun fieldsRow(): Row4<Long?, Long?, Long?, Boolean?> = super.fieldsRow() as Row4<Long?, Long?, Long?, Boolean?>
    override fun valuesRow(): Row4<Long?, Long?, Long?, Boolean?> = super.valuesRow() as Row4<Long?, Long?, Long?, Boolean?>
    override fun field1(): Field<Long?> = Classhierarchies.CLASSHIERARCHIES.ID
    override fun field2(): Field<Long?> = Classhierarchies.CLASSHIERARCHIES.CLASS_ID
    override fun field3(): Field<Long?> = Classhierarchies.CLASSHIERARCHIES.SUPER_ID
    override fun field4(): Field<Boolean?> = Classhierarchies.CLASSHIERARCHIES.IS_CLASS_REF
    override fun component1(): Long? = id
    override fun component2(): Long? = classId
    override fun component3(): Long? = superId
    override fun component4(): Boolean? = isClassRef
    override fun value1(): Long? = id
    override fun value2(): Long? = classId
    override fun value3(): Long? = superId
    override fun value4(): Boolean? = isClassRef

    override fun value1(value: Long?): ClasshierarchiesRecord {
        this.id = value
        return this
    }

    override fun value2(value: Long?): ClasshierarchiesRecord {
        this.classId = value
        return this
    }

    override fun value3(value: Long?): ClasshierarchiesRecord {
        this.superId = value
        return this
    }

    override fun value4(value: Boolean?): ClasshierarchiesRecord {
        this.isClassRef = value
        return this
    }

    override fun values(value1: Long?, value2: Long?, value3: Long?, value4: Boolean?): ClasshierarchiesRecord {
        this.value1(value1)
        this.value2(value2)
        this.value3(value3)
        this.value4(value4)
        return this
    }

    /**
     * Create a detached, initialised ClasshierarchiesRecord
     */
    constructor(id: Long? = null, classId: Long? = null, superId: Long? = null, isClassRef: Boolean? = null): this() {
        this.id = id
        this.classId = classId
        this.superId = superId
        this.isClassRef = isClassRef
    }
}
