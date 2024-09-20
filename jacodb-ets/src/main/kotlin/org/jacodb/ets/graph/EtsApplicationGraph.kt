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

package org.jacodb.ets.graph

import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.callExpr

interface EtsApplicationGraph : ApplicationGraph<EtsMethod, EtsStmt> {
    val cp: EtsScene
}

private fun EtsFileSignature?.isUnknown(): Boolean =
    this == null || fileName.isBlank() || fileName == "_UnknownFileName"

private fun EtsClassSignature.isUnknown(): Boolean =
    name.isBlank()

enum class ComparisonResult {
    Equal,
    NotEqual,
    Unknown,
}

fun compareFileSignatures(
    sig1: EtsFileSignature?,
    sig2: EtsFileSignature?,
): ComparisonResult = when {
    sig1.isUnknown() -> ComparisonResult.Unknown
    sig2.isUnknown() -> ComparisonResult.Unknown
    sig1?.fileName == sig2?.fileName -> ComparisonResult.Equal
    else -> ComparisonResult.NotEqual
}

fun compareClassSignatures(
    sig1: EtsClassSignature,
    sig2: EtsClassSignature,
): ComparisonResult = when {
    sig1.isUnknown() -> ComparisonResult.Unknown
    sig2.isUnknown() -> ComparisonResult.Unknown
    sig1.name == sig2.name -> compareFileSignatures(sig1.file, sig2.file)
    else -> ComparisonResult.NotEqual
}

class EtsApplicationGraphImpl(
    override val cp: EtsScene,
) : EtsApplicationGraph {

    override fun predecessors(node: EtsStmt): Sequence<EtsStmt> {
        val graph = node.method.flowGraph()
        val predecessors = graph.predecessors(node)
        return predecessors.asSequence()
    }

    override fun successors(node: EtsStmt): Sequence<EtsStmt> {
        val graph = node.method.flowGraph()
        val successors = graph.successors(node)
        return successors.asSequence()
    }

    override fun callees(node: EtsStmt): Sequence<EtsMethod> {
        val expr = node.callExpr ?: return emptySequence()
        val callee = expr.method

        // First, try to resolve the callee via a complete signature match:
        val resolvedCompletely = cp.classes
            .asSequence()
            .filter { compareClassSignatures(it.signature, callee.enclosingClass) == ComparisonResult.Equal }
            // Note: include constructors!
            .flatMap { it.methods.asSequence() + it.ctor }
            .filter { it.name == callee.name }
        if (resolvedCompletely.any()) return resolvedCompletely

        // If the complete signature match failed,
        // try to find the unique neighbour (non-recursive) method in the same class:
        val resolvedNeighbour = cp.classes
            .single { it.signature == node.method.enclosingClass }
            .methods
            .asSequence()
            .filter { it.name == callee.name }
            .filterNot { it.name == node.method.name }
        if (resolvedNeighbour.any()) return resolvedNeighbour

        // If the neighbour match failed,
        // try to *uniquely* resolve the callee via a partial signature match:
        val resolvedPartially = cp.classes
            .asSequence()
            .filter { compareClassSignatures(it.signature, callee.enclosingClass) != ComparisonResult.NotEqual }
            // Note: omit constructors!
            .flatMap { it.methods.asSequence() }
            .filter { it.name == callee.name }
            // Note: exclude recursive calls:
            .filterNot { it.name == node.method.name }

        if (resolvedPartially.none()) return emptySequence()
        val resolved = resolvedPartially.toList()
        if (resolved.size == 1) return resolved.asSequence()
        return emptySequence()
    }

    override fun callers(method: EtsMethod): Sequence<EtsStmt> {
        // Note: currently, nobody uses `callers`, so if is safe to disable it for now.
        // Note: comparing methods by signature may be incorrect, and comparing only by name fails for constructors.
        TODO("disabled for now, need re-design")
        // return cp.classes.asSequence()
        //     .flatMap { it.methods }
        //     .flatMap { it.cfg.instructions }
        //     .filterIsInstance<EtsCallStmt>()
        //     .filter { it.expr.method == method.signature }
    }

    override fun entryPoints(method: EtsMethod): Sequence<EtsStmt> {
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: EtsMethod): Sequence<EtsStmt> {
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: EtsStmt): EtsMethod {
        return node.location.method
    }
}
