package org.utbot.jcdb.impl.storage

import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Table
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement
import java.sql.Types

val String.longHash: Long
    get() {
        var h = 1125899906842597L // prime
        val len = length
        for (i in 0 until len) {
            h = 31 * h + this[i].code.toLong()
        }
        return h
    }


fun <ELEMENT, RECORD : Record> Connection.insertElements(
    table: Table<RECORD>,
    elements: Iterable<ELEMENT>,
    map: PreparedStatement.(ELEMENT) -> Unit
) {
    if (!elements.iterator().hasNext()) {
        return
    }
    autoCommit = false
    val fields = table.fields()
    val query =
        "INSERT INTO ${table.name}(${fields.joinToString { "\"" + it.name + "\"" }}) VALUES (${fields.joinToString { "?" }})"
    prepareStatement(query, Statement.NO_GENERATED_KEYS).use { stmt ->
        elements.forEach {
            stmt.map(it)
            stmt.addBatch()
        }
        stmt.executeBatch()
    }
    commit()
    autoCommit = true
}

fun <RECORD : Record> Connection.runBatch(table: Table<RECORD>, batchedAction: PreparedStatement.() -> Unit) {
    autoCommit = false
    val fields = table.fields()
    val query =
        "INSERT INTO ${table.name}(${fields.joinToString { "\"" + it.name + "\"" }}) VALUES (${fields.joinToString { "?" }})"
    prepareStatement(query, Statement.NO_GENERATED_KEYS).use { stmt ->
        stmt.batchedAction()
        stmt.executeBatch()
    }
    commit()
    autoCommit = true
}

fun DSLContext.executeQueries(query: String) {
    connection {
        query.split(";").forEach {
            if (it.isNotBlank()) {
                execute(it)
            }
        }
    }
}

fun DSLContext.executeQueriesFrom(location: String) {
    val stream = javaClass.classLoader.getResourceAsStream(location)
        ?: throw IllegalStateException("no sql script for $location found")
    executeQueries(stream.bufferedReader().readText())
}

fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
    if (value == null) {
        setNull(index, Types.BIGINT)
    } else {
        setLong(index, value)
    }
}

fun <T> Field<T>.eqOrNull(value: T?): Condition {
    if (value == null) {
        return isNull
    }
    return eq(value)
}