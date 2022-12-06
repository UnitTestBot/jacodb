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
package org.utbot.jcdb.impl.storage.jooq.tables.records


import org.jooq.Field
import org.jooq.Record1
import org.jooq.Record6
import org.jooq.Row6
import org.jooq.impl.UpdatableRecordImpl
import org.utbot.jcdb.impl.storage.jooq.tables.Bytecodelocations


/**
 * This class is generated by jOOQ.
 */
@Suppress("UNCHECKED_CAST")
open class BytecodelocationsRecord() : UpdatableRecordImpl<BytecodelocationsRecord>(Bytecodelocations.BYTECODELOCATIONS), Record6<Long?, String?, String?, Boolean?, Int?, Long?> {

    var id: Long?
        set(value) = set(0, value)
        get() = get(0) as Long?

    var path: String?
        set(value) = set(1, value)
        get() = get(1) as String?

    var uniqueid: String?
        set(value) = set(2, value)
        get() = get(2) as String?

    var runtime: Boolean?
        set(value) = set(3, value)
        get() = get(3) as Boolean?

    var state: Int?
        set(value) = set(4, value)
        get() = get(4) as Int?

    var updatedId: Long?
        set(value) = set(5, value)
        get() = get(5) as Long?

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    override fun key(): Record1<Long?> = super.key() as Record1<Long?>

    // -------------------------------------------------------------------------
    // Record6 type implementation
    // -------------------------------------------------------------------------

    override fun fieldsRow(): Row6<Long?, String?, String?, Boolean?, Int?, Long?> = super.fieldsRow() as Row6<Long?, String?, String?, Boolean?, Int?, Long?>
    override fun valuesRow(): Row6<Long?, String?, String?, Boolean?, Int?, Long?> = super.valuesRow() as Row6<Long?, String?, String?, Boolean?, Int?, Long?>
    override fun field1(): Field<Long?> = Bytecodelocations.BYTECODELOCATIONS.ID
    override fun field2(): Field<String?> = Bytecodelocations.BYTECODELOCATIONS.PATH
    override fun field3(): Field<String?> = Bytecodelocations.BYTECODELOCATIONS.UNIQUEID
    override fun field4(): Field<Boolean?> = Bytecodelocations.BYTECODELOCATIONS.RUNTIME
    override fun field5(): Field<Int?> = Bytecodelocations.BYTECODELOCATIONS.STATE
    override fun field6(): Field<Long?> = Bytecodelocations.BYTECODELOCATIONS.UPDATED_ID
    override fun component1(): Long? = id
    override fun component2(): String? = path
    override fun component3(): String? = uniqueid
    override fun component4(): Boolean? = runtime
    override fun component5(): Int? = state
    override fun component6(): Long? = updatedId
    override fun value1(): Long? = id
    override fun value2(): String? = path
    override fun value3(): String? = uniqueid
    override fun value4(): Boolean? = runtime
    override fun value5(): Int? = state
    override fun value6(): Long? = updatedId

    override fun value1(value: Long?): BytecodelocationsRecord {
        this.id = value
        return this
    }

    override fun value2(value: String?): BytecodelocationsRecord {
        this.path = value
        return this
    }

    override fun value3(value: String?): BytecodelocationsRecord {
        this.uniqueid = value
        return this
    }

    override fun value4(value: Boolean?): BytecodelocationsRecord {
        this.runtime = value
        return this
    }

    override fun value5(value: Int?): BytecodelocationsRecord {
        this.state = value
        return this
    }

    override fun value6(value: Long?): BytecodelocationsRecord {
        this.updatedId = value
        return this
    }

    override fun values(value1: Long?, value2: String?, value3: String?, value4: Boolean?, value5: Int?, value6: Long?): BytecodelocationsRecord {
        this.value1(value1)
        this.value2(value2)
        this.value3(value3)
        this.value4(value4)
        this.value5(value5)
        this.value6(value6)
        return this
    }

    /**
     * Create a detached, initialised BytecodelocationsRecord
     */
    constructor(id: Long? = null, path: String? = null, uniqueid: String? = null, runtime: Boolean? = null, state: Int? = null, updatedId: Long? = null): this() {
        this.id = id
        this.path = path
        this.uniqueid = uniqueid
        this.runtime = runtime
        this.state = state
        this.updatedId = updatedId
    }
}
