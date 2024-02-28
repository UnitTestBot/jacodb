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

package org.jacodb.analysis.unused

import org.jacodb.analysis.ifds.AccessPath
import org.jacodb.analysis.util.Traits
import org.jacodb.analysis.util.values
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.ext.callExpr
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBranchingInst
import org.jacodb.api.jvm.cfg.JcLocal
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcTerminatingInst

context(Traits<CommonMethod<*, *>, CommonInst<*, *>>)
internal fun AccessPath.isUsedAt(
    expr: CommonExpr,
): Boolean {
    return expr.values.any { it.toPathOrNull() == this }
}

context(Traits<CommonMethod<*, *>, CommonInst<*, *>>)
internal fun AccessPath.isUsedAt(
    inst: CommonInst<*, *>,
): Boolean {
    val callExpr = inst.callExpr

    if (callExpr != null) {
        // Don't count constructor calls as usages
        if (callExpr is JcSpecialCallExpr
            && callExpr.method.method.isConstructor
            && isUsedAt(callExpr.instance)
        ) {
            return false
        }

        return isUsedAt(callExpr)
    }
    if (inst is JcAssignInst) {
        if (inst.lhv is JcArrayAccess && isUsedAt(inst.lhv)) {
            return true
        }
        return isUsedAt(inst.rhv) && (inst.lhv !is JcLocal || inst.rhv !is JcLocal)
    }
    if (inst is JcTerminatingInst || inst is JcBranchingInst) {
        return inst.operands.any { isUsedAt(it) }
    }
    return false
}
