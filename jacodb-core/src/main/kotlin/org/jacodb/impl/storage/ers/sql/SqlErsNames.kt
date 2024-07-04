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

package org.jacodb.impl.storage.ers.sql

import org.jooq.Name
import org.jooq.Table
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

object SqlErsNames {
    const val ENTITY_TABLE_PREFIX = "Entity_"
    const val PROPERTY_TABLE_PREFIX = "Property_"
    const val BLOB_TABLE_PREFIX = "Blob_"
    const val LINK_TABLE_PREFIX = "Link_"

    private val LINK_TABLE_NAME_REGEX = "^${LINK_TABLE_PREFIX}(\\d+)_(\\d+)#(.+)$".toRegex()

    val DYNAMIC_TABLE_PREFIXES = setOf(ENTITY_TABLE_PREFIX, PROPERTY_TABLE_PREFIX, BLOB_TABLE_PREFIX, LINK_TABLE_PREFIX)

    val ENTITY_ID_FIELD = DSL.field(DSL.name("entityId"), SQLDataType.BIGINT.nullable(false))
    val PROPERTY_VALUE_FIELD = DSL.field(DSL.name("value"), SQLDataType.BLOB.nullable(false))
    val BLOB_VALUE_FIELD = DSL.field(DSL.name("value"), SQLDataType.BLOB.nullable(false))

    val LINK_SOURCE_ENTITY_ID_FIELD = DSL.field(DSL.name("linkSource"), SQLDataType.BIGINT.nullable(false))
    val LINK_TARGET_ENTITY_ID_FIELD = DSL.field(DSL.name("linkTarget"), SQLDataType.BIGINT.nullable(false))

    fun getEntityTableNameByTypeId(typeId: Int): Name =
            DSL.name("${ENTITY_TABLE_PREFIX}$typeId")

    fun getPropertyTableName(typeId: Int, propertyName: String): Name =
            DSL.name("${PROPERTY_TABLE_PREFIX}$typeId#$propertyName")

    fun getBlobTableName(typeId: Int, blobName: String): Name =
        DSL.name("${BLOB_TABLE_PREFIX}$typeId#$blobName")

    fun getLinkTableName(linkTableMetaData: LinkTableMetaData): Name = with(linkTableMetaData) {
        DSL.name("${LINK_TABLE_PREFIX}${sourceTypeId}_${targetTypeId}#${linkName}")
    }

    fun parseLinkTableNameOrNull(table: Table<*>): LinkTableMetaData? {
        return LINK_TABLE_NAME_REGEX.find(table.name)?.let { matchResult ->
            val (sourceTypeId, targetTypeId, linkName) = matchResult.destructured
            LinkTableMetaData(sourceTypeId.toInt(), targetTypeId.toInt(), linkName)
        }
    }
}

data class LinkTableMetaData(
    val sourceTypeId: Int,
    val targetTypeId: Int,
    val linkName: String
)
