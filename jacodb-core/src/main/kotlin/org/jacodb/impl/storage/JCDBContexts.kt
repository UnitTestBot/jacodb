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

import org.jacodb.api.jvm.ContextProperty
import org.jacodb.api.jvm.JCDBContext
import org.jacodb.api.jvm.invoke
import org.jacodb.api.jvm.storage.ers.Transaction
import org.jooq.DSLContext
import java.sql.Connection

private object DSLContextProperty : ContextProperty<DSLContext> {
    override fun toString() = "dslContext"
}

private object ConnectionProperty : ContextProperty<Connection> {
    override fun toString() = "connection"
}

private object ERSTransactionProperty : ContextProperty<Transaction> {
    override fun toString() = "transaction"
}

fun toJCDBContext(dslContext: DSLContext, connection: Connection): JCDBContext =
    toJCDBContext(dslContext)(ConnectionProperty, connection)

fun toJCDBContext(dslContext: DSLContext): JCDBContext = JCDBContext.of(DSLContextProperty, dslContext)

val JCDBContext.dslContext: DSLContext get() = getContextObject(DSLContextProperty)

val JCDBContext.connection: Connection get() = getContextObject(ConnectionProperty)

val JCDBContext.isSqlContext: Boolean get() = hasContextObject(DSLContextProperty)

fun toJCDBContext(txn: Transaction) = JCDBContext.of(ERSTransactionProperty, txn)

val JCDBContext.txn: Transaction get() = getContextObject(ERSTransactionProperty)

val JCDBContext.isErsContext: Boolean get() = hasContextObject(ERSTransactionProperty)

fun <T> JCDBContext.execute(sqlAction: (DSLContext) -> T, noSqlAction: (Transaction) -> T): T {
    return if (isSqlContext) {
        sqlAction(dslContext)
    } else if (isErsContext) {
        noSqlAction(txn)
    } else {
        throw IllegalArgumentException("JCDBContext should support SQL or NoSQL persistence")
    }
}