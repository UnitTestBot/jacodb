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

package org.jacodb.analysis.ifds

import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcSimpleValue
import org.jacodb.api.jvm.cfg.JcValue

interface CommonAccessPath {
    val value: CommonValue?
    val accesses: List<Accessor>

    fun limit(n: Int): CommonAccessPath

    operator fun plus(accesses: List<Accessor>): CommonAccessPath
    operator fun plus(accessor: Accessor): CommonAccessPath
}

val CommonAccessPath.isOnHeap: Boolean
    get() = accesses.isNotEmpty()

val CommonAccessPath.isStatic: Boolean
    get() = value == null

operator fun CommonAccessPath.minus(other: CommonAccessPath): List<Accessor>? {
    if (value != other.value) return null
    if (accesses.take(other.accesses.size) != other.accesses) return null
    return accesses.drop(other.accesses.size)
}

fun CommonExpr.toPathOrNull(): CommonAccessPath? = when (this) {
    is JcExpr -> toPathOrNull()
    is CommonValue -> toPathOrNull()
    else -> error("Cannot")
}

fun CommonValue.toPathOrNull(): CommonAccessPath? = when (this) {
    is JcValue -> toPathOrNull()
    else -> error("Cannot")
}

fun CommonValue.toPath(): CommonAccessPath = when (this) {
    is JcValue -> toPath()
    else -> error("Cannot")
}

/**
 * This class is used to represent an access path that is needed for problems
 * where dataflow facts could be correlated with variables/values
 * (such as NPE, uninitialized variable, etc.)
 */
data class JcAccessPath internal constructor(
    override val value: JcSimpleValue?, // null for static field
    override val accesses: List<Accessor>,
) : CommonAccessPath {
    init {
        if (value == null) {
            require(accesses.isNotEmpty())
            val a = accesses[0]
            require(a is FieldAccessor)
            require(a.field is JcField)
            require(a.field.isStatic)
        }
    }

    override fun limit(n: Int): JcAccessPath = JcAccessPath(value, accesses.take(n))

    override operator fun plus(accesses: List<Accessor>): JcAccessPath {
        for (accessor in accesses) {
            if (accessor is FieldAccessor && (accessor.field as JcField).isStatic) {
                throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
            }
        }

        return JcAccessPath(value, this.accesses + accesses)
    }

    override operator fun plus(accessor: Accessor): JcAccessPath {
        if (accessor is FieldAccessor && (accessor.field as JcField).isStatic) {
            throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
        }

        return JcAccessPath(value, this.accesses + accessor)
    }

    override fun toString(): String {
        return value.toString() + accesses.joinToString("") { it.toSuffix() }
    }

    companion object {
        fun from(value: JcSimpleValue): JcAccessPath = JcAccessPath(value, emptyList())

        fun from(field: JcField): JcAccessPath {
            require(field.isStatic) { "Expected static field" }
            return JcAccessPath(null, listOf(FieldAccessor(field)))
        }
    }
}

fun JcExpr.toPathOrNull(): JcAccessPath? = when (this) {
    is JcValue -> toPathOrNull()
    is JcCastExpr -> operand.toPathOrNull()
    else -> null
}

fun JcValue.toPathOrNull(): JcAccessPath? = when (this) {
    is JcSimpleValue -> JcAccessPath.from(this)

    is JcArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is JcFieldRef -> {
        val instance = instance
        if (instance == null) {
            JcAccessPath.from(field.field)
        } else {
            instance.toPathOrNull()?.let {
                it + FieldAccessor(field.field)
            }
        }
    }

    else -> null
}

fun JcValue.toPath(): JcAccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}
