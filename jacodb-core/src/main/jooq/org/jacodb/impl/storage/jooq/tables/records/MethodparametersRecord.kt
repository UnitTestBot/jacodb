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


import org.jacodb.impl.storage.jooq.tables.Methodparameters
import org.jooq.Field
import org.jooq.Record1
import org.jooq.Record6
import org.jooq.Row6
import org.jooq.impl.UpdatableRecordImpl


/**
 * This class is generated by jOOQ.
 */
@Suppress("UNCHECKED_CAST")
open class MethodparametersRecord() : UpdatableRecordImpl<MethodparametersRecord>(Methodparameters.METHODPARAMETERS), Record6<Long?, Int?, Int?, String?, Long?, Long?> {

    var id: Long?
        set(value) = set(0, value)
        get() = get(0) as Long?

    var access: Int?
        set(value) = set(1, value)
        get() = get(1) as Int?

    var index: Int?
        set(value) = set(2, value)
        get() = get(2) as Int?

    var name: String?
        set(value) = set(3, value)
        get() = get(3) as String?

    var parameterClass: Long?
        set(value) = set(4, value)
        get() = get(4) as Long?

    var methodId: Long?
        set(value) = set(5, value)
        get() = get(5) as Long?

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    override fun key(): Record1<Long?> = super.key() as Record1<Long?>

    // -------------------------------------------------------------------------
    // Record6 type implementation
    // -------------------------------------------------------------------------

    override fun fieldsRow(): Row6<Long?, Int?, Int?, String?, Long?, Long?> = super.fieldsRow() as Row6<Long?, Int?, Int?, String?, Long?, Long?>
    override fun valuesRow(): Row6<Long?, Int?, Int?, String?, Long?, Long?> = super.valuesRow() as Row6<Long?, Int?, Int?, String?, Long?, Long?>
    override fun field1(): Field<Long?> = Methodparameters.METHODPARAMETERS.ID
    override fun field2(): Field<Int?> = Methodparameters.METHODPARAMETERS.ACCESS
    override fun field3(): Field<Int?> = Methodparameters.METHODPARAMETERS.INDEX
    override fun field4(): Field<String?> = Methodparameters.METHODPARAMETERS.NAME
    override fun field5(): Field<Long?> = Methodparameters.METHODPARAMETERS.PARAMETER_CLASS
    override fun field6(): Field<Long?> = Methodparameters.METHODPARAMETERS.METHOD_ID
    override fun component1(): Long? = id
    override fun component2(): Int? = access
    override fun component3(): Int? = index
    override fun component4(): String? = name
    override fun component5(): Long? = parameterClass
    override fun component6(): Long? = methodId
    override fun value1(): Long? = id
    override fun value2(): Int? = access
    override fun value3(): Int? = index
    override fun value4(): String? = name
    override fun value5(): Long? = parameterClass
    override fun value6(): Long? = methodId

    override fun value1(value: Long?): MethodparametersRecord {
        this.id = value
        return this
    }

    override fun value2(value: Int?): MethodparametersRecord {
        this.access = value
        return this
    }

    override fun value3(value: Int?): MethodparametersRecord {
        this.index = value
        return this
    }

    override fun value4(value: String?): MethodparametersRecord {
        this.name = value
        return this
    }

    override fun value5(value: Long?): MethodparametersRecord {
        this.parameterClass = value
        return this
    }

    override fun value6(value: Long?): MethodparametersRecord {
        this.methodId = value
        return this
    }

    override fun values(value1: Long?, value2: Int?, value3: Int?, value4: String?, value5: Long?, value6: Long?): MethodparametersRecord {
        this.value1(value1)
        this.value2(value2)
        this.value3(value3)
        this.value4(value4)
        this.value5(value5)
        this.value6(value6)
        return this
    }

    /**
     * Create a detached, initialised MethodparametersRecord
     */
    constructor(id: Long? = null, access: Int? = null, index: Int? = null, name: String? = null, parameterClass: Long? = null, methodId: Long? = null): this() {
        this.id = id
        this.access = access
        this.index = index
        this.name = name
        this.parameterClass = parameterClass
        this.methodId = methodId
    }
}
