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
import org.jooq.Record5
import org.jooq.Row5
import org.jooq.impl.TableRecordImpl
import org.utbot.jcdb.impl.storage.jooq.tables.Builders


/**
 * This class is generated by jOOQ.
 */
@Suppress("UNCHECKED_CAST")
open class BuildersRecord() : TableRecordImpl<BuildersRecord>(Builders.BUILDERS), Record5<Long?, Long?, Int?, Int?, Long?> {

    var classSymbolId: Long?
        set(value) = set(0, value)
        get() = get(0) as Long?

    var builderClassSymbolId: Long?
        set(value) = set(1, value)
        get() = get(1) as Long?

    var priority: Int?
        set(value) = set(2, value)
        get() = get(2) as Int?

    var offset: Int?
        set(value) = set(3, value)
        get() = get(3) as Int?

    var locationId: Long?
        set(value) = set(4, value)
        get() = get(4) as Long?

    // -------------------------------------------------------------------------
    // Record5 type implementation
    // -------------------------------------------------------------------------

    override fun fieldsRow(): Row5<Long?, Long?, Int?, Int?, Long?> = super.fieldsRow() as Row5<Long?, Long?, Int?, Int?, Long?>
    override fun valuesRow(): Row5<Long?, Long?, Int?, Int?, Long?> = super.valuesRow() as Row5<Long?, Long?, Int?, Int?, Long?>
    override fun field1(): Field<Long?> = Builders.BUILDERS.CLASS_SYMBOL_ID
    override fun field2(): Field<Long?> = Builders.BUILDERS.BUILDER_CLASS_SYMBOL_ID
    override fun field3(): Field<Int?> = Builders.BUILDERS.PRIORITY
    override fun field4(): Field<Int?> = Builders.BUILDERS.OFFSET
    override fun field5(): Field<Long?> = Builders.BUILDERS.LOCATION_ID
    override fun component1(): Long? = classSymbolId
    override fun component2(): Long? = builderClassSymbolId
    override fun component3(): Int? = priority
    override fun component4(): Int? = offset
    override fun component5(): Long? = locationId
    override fun value1(): Long? = classSymbolId
    override fun value2(): Long? = builderClassSymbolId
    override fun value3(): Int? = priority
    override fun value4(): Int? = offset
    override fun value5(): Long? = locationId

    override fun value1(value: Long?): BuildersRecord {
        this.classSymbolId = value
        return this
    }

    override fun value2(value: Long?): BuildersRecord {
        this.builderClassSymbolId = value
        return this
    }

    override fun value3(value: Int?): BuildersRecord {
        this.priority = value
        return this
    }

    override fun value4(value: Int?): BuildersRecord {
        this.offset = value
        return this
    }

    override fun value5(value: Long?): BuildersRecord {
        this.locationId = value
        return this
    }

    override fun values(value1: Long?, value2: Long?, value3: Int?, value4: Int?, value5: Long?): BuildersRecord {
        this.value1(value1)
        this.value2(value2)
        this.value3(value3)
        this.value4(value4)
        this.value5(value5)
        return this
    }

    /**
     * Create a detached, initialised BuildersRecord
     */
    constructor(classSymbolId: Long? = null, builderClassSymbolId: Long? = null, priority: Int? = null, offset: Int? = null, locationId: Long? = null): this() {
        this.classSymbolId = classSymbolId
        this.builderClassSymbolId = builderClassSymbolId
        this.priority = priority
        this.offset = offset
        this.locationId = locationId
    }
}
