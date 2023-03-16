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

package org.jacodb.analysis.impl

import org.jacodb.api.JcClassType
import org.jacodb.api.JcField
import org.jacodb.api.cfg.JcCastExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcFieldRef
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.fields

/**
 * This class is used to represent an access path that is needed for problems
 * where dataflow facts could be correlated with variables/values (such as NPE, uninitialized variable, etc.)
 */
data class AccessPath private constructor(val value: JcLocal, val fieldAccesses: List<JcField>) {
    companion object {

        fun fromLocal(value: JcLocal) = AccessPath(value, listOf())

        fun fromOther(other: AccessPath, fields: List<JcField>, maxLength: Int) = AccessPath(other.value, other.fieldAccesses.plus(fields).take(maxLength))
    }

    val isOnHeap: Boolean
        get() = fieldAccesses.isNotEmpty()

    override fun toString(): String {
        var str = value.toString()
        for (field in fieldAccesses) {
            str += ".${field.name}"
        }
        return str
    }
}

internal fun JcExpr.toPathOrNull(maxPathLength: Int): AccessPath? {
    if (this is JcCastExpr) {
        return operand.toPathOrNull(maxPathLength)
    }
    if (this is JcLocal) {
        return AccessPath.fromLocal(this)
    }
    if (this is JcFieldRef) {
        // TODO: think about static fields
        return instance?.toPathOrNull(maxPathLength)?.let {
            AccessPath.fromOther(it, listOf(field.field), maxPathLength)
        }
    }
    // TODO: handle arrays
    return null
}

internal fun JcValue.toPath(maxPathLength: Int): AccessPath {
    return toPathOrNull(maxPathLength) ?: error("Unable to build access path for value $this")
}

internal fun AccessPath.expandAtDepth(k: Int): List<AccessPath> {
    if (k == 0)
        return listOf(this)

    val jcClass = fieldAccesses.lastOrNull()?.enclosingClass ?: (value.type as? JcClassType)?.jcClass
    ?: return listOf(this)
    // TODO: handle ArrayType

    return listOf(this) + jcClass.fields.flatMap {
        AccessPath.fromOther(this, listOf(it), k).expandAtDepth(k - 1)
    }
}

internal fun AccessPath?.minus(other: AccessPath): List<JcField>? {
    if (this == null) {
        return null
    }
    if (value != other.value) {
        return null
    }
    if (fieldAccesses.take(other.fieldAccesses.size) != other.fieldAccesses) {
        return null
    }
    return fieldAccesses.drop(other.fieldAccesses.size)
}

internal fun AccessPath?.startsWith(other: AccessPath): Boolean {
    return minus(other) != null
}