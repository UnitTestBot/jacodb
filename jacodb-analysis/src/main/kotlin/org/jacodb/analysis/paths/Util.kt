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

package org.jacodb.analysis.paths

import org.jacodb.api.cfg.JcArrayAccess
import org.jacodb.api.cfg.JcCastExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcFieldRef
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcLengthExpr
import org.jacodb.api.cfg.JcSimpleValue
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.cfg.values

/**
 * Converts `JcExpr` (in particular, `JcValue`) to `AccessPath`.
 *   - For `JcSimpleValue`, this method simply wraps the value.
 *   - For `JcArrayAccess` and `JcFieldRef`, this method "reverses" it and recursively constructs a list of accessors (`ElementAccessor` for array access, `FieldAccessor` for field access).
 *   - Returns `null` when the conversion to `AccessPath` is not possible.
 *
 * Example:
 *   `x.f[0].y` is `AccessPath(value = x, accesses = [Field(f), Element(0), Field(y)])`
 */
internal fun JcExpr.toPathOrNull(): AccessPath? {
    return when (this) {
        is JcSimpleValue -> {
            AccessPath.from(this)
        }

        is JcCastExpr -> {
            operand.toPathOrNull()
        }

        is JcArrayAccess -> {
            array.toPathOrNull()?.let {
                it / listOf(ElementAccessor(index))
            }
        }

        is JcFieldRef -> {
            val instance = instance // enables smart cast

            if (instance == null) {
                AccessPath.fromStaticField(field.field)
            } else {
                instance.toPathOrNull()?.let { it / FieldAccessor(field.field) }
            }
        }

        else -> null
    }
}

internal fun JcValue.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}

// this = value.x.y[0]
// this = (value, accesses = listOf(Field, Field, Element))
//
// other = value.x
// other = (value, accesses = listOf(Field))
//
// (this - other) = listOf(Field, Element) = ".y[0]"
internal operator fun AccessPath?.minus(other: AccessPath): List<Accessor>? {
    if (this == null) {
        return null
    }
    if (value != other.value) {
        return null
    }
    if (this.accesses.take(other.accesses.size) != other.accesses) {
        return null
    }

    return accesses.drop(other.accesses.size)
}

internal fun AccessPath?.startsWith(other: AccessPath?): Boolean {
    if (this == null || other == null) {
        return false
    }
    if (this.value != other.value) {
        return false
    }
    // Unnecessary check:
    // if (this.accesses.size < other.accesses.size) {
    //     return false
    // }
    return this.accesses.take(other.accesses.size) == other.accesses
}

fun AccessPath?.isDereferencedAt(expr: JcExpr): Boolean {
    if (this == null) {
        return false
    }

    if (expr is JcInstanceCallExpr) {
        val instancePath = expr.instance.toPathOrNull()
        if (instancePath.startsWith(this)) {
            return true
        }
    }

    if (expr is JcLengthExpr) {
        val arrayPath = expr.array.toPathOrNull()
        if (arrayPath.startsWith(this)) {
            return true
        }
    }

    return expr.values
        .mapNotNull { it.toPathOrNull() }
        .any {
            (it - this)?.isNotEmpty() == true
        }
}

fun AccessPath?.isDereferencedAt(inst: JcInst): Boolean {
    if (this == null) {
        return false
    }

    return inst.operands.any { isDereferencedAt(it) }
}
