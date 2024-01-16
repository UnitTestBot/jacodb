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

import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstanceCallExpr
import org.jacodb.api.jvm.cfg.JcLengthExpr
import org.jacodb.api.jvm.cfg.JcLocal
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.cfg.values

internal fun JcExpr.toPathOrNull(): AccessPath? {
    if (this is JcCastExpr) {
        return operand.toPathOrNull()
    }
    if (this is JcLocal) {
        return AccessPath.fromLocal(this)
    }

    if (this is JcArrayAccess) {
        return array.toPathOrNull()?.let {
            AccessPath.fromOther(it, listOf(ElementAccessor))
        }
    }

    if (this is JcFieldRef) {
        val instance = instance // enables smart cast

        return if (instance == null) {
            AccessPath.fromStaticField(field.field)
        } else {
            instance.toPathOrNull()?.let {
                AccessPath.fromOther(it, listOf(FieldAccessor(field.field)))
            }
        }
    }
    return null
}

internal fun JcValue.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}

internal fun AccessPath?.minus(other: AccessPath): List<Accessor>? {
    if (this == null) {
        return null
    }
    if (value != other.value) {
        return null
    }
    if (accesses.take(other.accesses.size) != other.accesses) {
        return null
    }

    return accesses.drop(other.accesses.size)
}

internal fun AccessPath?.startsWith(other: AccessPath?): Boolean {
    if (this == null || other == null) {
        return false
    }

    return minus(other) != null
}

fun AccessPath?.isDereferencedAt(expr: JcExpr): Boolean {
    if (this == null) {
        return false
    }

    (expr as? JcInstanceCallExpr)?.let {
        val instancePath = it.instance.toPathOrNull()
        if (instancePath.startsWith(this)) {
            return true
        }
    }

    (expr as? JcLengthExpr)?.let {
        val arrayPath = it.array.toPathOrNull()
        if (arrayPath.startsWith(this)) {
            return true
        }
    }

    return expr.values
        .mapNotNull { it.toPathOrNull() }
        .any {
            it.minus(this)?.isNotEmpty() == true
        }
}

fun AccessPath?.isDereferencedAt(inst: JcInst): Boolean {
    if (this == null) {
        return false
    }

    return inst.operands.any { isDereferencedAt(it) }
}