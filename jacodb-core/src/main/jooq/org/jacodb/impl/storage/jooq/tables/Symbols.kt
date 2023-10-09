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
package org.jacodb.impl.storage.jooq.tables


import org.jacodb.impl.storage.jooq.DefaultSchema
import org.jacodb.impl.storage.jooq.indexes.SYMBOLS_NAME
import org.jacodb.impl.storage.jooq.keys.PK_SYMBOLS
import org.jacodb.impl.storage.jooq.tables.records.SymbolsRecord
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl


/**
 * This class is generated by jOOQ.
 */
@Suppress("UNCHECKED_CAST")
open class Symbols(
    alias: Name,
    child: Table<out Record>?,
    path: ForeignKey<out Record, SymbolsRecord>?,
    aliased: Table<SymbolsRecord>?,
    parameters: Array<Field<*>?>?
): TableImpl<SymbolsRecord>(
    alias,
    DefaultSchema.DEFAULT_SCHEMA,
    child,
    path,
    aliased,
    parameters,
    DSL.comment(""),
    TableOptions.table()
) {
    companion object {

        /**
         * The reference instance of <code>Symbols</code>
         */
        val SYMBOLS = Symbols()
    }

    /**
     * The class holding records for this type
     */
    override fun getRecordType(): Class<SymbolsRecord> = SymbolsRecord::class.java

    /**
     * The column <code>Symbols.id</code>.
     */
    val ID: TableField<SymbolsRecord, Long?> = createField(DSL.name("id"), SQLDataType.BIGINT, this, "")

    /**
     * The column <code>Symbols.name</code>.
     */
    val NAME: TableField<SymbolsRecord, String?> = createField(DSL.name("name"), SQLDataType.VARCHAR(256).nullable(false), this, "")

    private constructor(alias: Name, aliased: Table<SymbolsRecord>?): this(alias, null, null, aliased, null)
    private constructor(alias: Name, aliased: Table<SymbolsRecord>?, parameters: Array<Field<*>?>?): this(alias, null, null, aliased, parameters)

    /**
     * Create an aliased <code>Symbols</code> table reference
     */
    constructor(alias: String): this(DSL.name(alias))

    /**
     * Create an aliased <code>Symbols</code> table reference
     */
    constructor(alias: Name): this(alias, null)

    /**
     * Create a <code>Symbols</code> table reference
     */
    constructor(): this(DSL.name("Symbols"), null)

    constructor(child: Table<out Record>, key: ForeignKey<out Record, SymbolsRecord>): this(Internal.createPathAlias(child, key), child, key, SYMBOLS, null)
    override fun getSchema(): Schema = DefaultSchema.DEFAULT_SCHEMA
    override fun getIndexes(): List<Index> = listOf(SYMBOLS_NAME)
    override fun getPrimaryKey(): UniqueKey<SymbolsRecord> = PK_SYMBOLS
    override fun getKeys(): List<UniqueKey<SymbolsRecord>> = listOf(PK_SYMBOLS)
    override fun `as`(alias: String): Symbols = Symbols(DSL.name(alias), this)
    override fun `as`(alias: Name): Symbols = Symbols(alias, this)

    /**
     * Rename this table
     */
    override fun rename(name: String): Symbols = Symbols(DSL.name(name), null)

    /**
     * Rename this table
     */
    override fun rename(name: Name): Symbols = Symbols(name, null)

    // -------------------------------------------------------------------------
    // Row2 type methods
    // -------------------------------------------------------------------------
    override fun fieldsRow(): Row2<Long?, String?> = super.fieldsRow() as Row2<Long?, String?>
}
