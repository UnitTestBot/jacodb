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

import mu.KotlinLogging
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.callExpr
import org.jacodb.impl.util.Maybe

private val logger = KotlinLogging.logger {}

interface EtsApplicationGraph : ApplicationGraph<EtsMethod, EtsStmt> {
    val cp: EtsScene
}

private fun EtsFileSignature?.isUnknown(): Boolean =
    this == null || fileName.isBlank() || fileName == "_UnknownFileName"

private fun EtsClassSignature.isUnknown(): Boolean =
    name.isBlank()

private fun EtsClassSignature.isIdeal(): Boolean =
    !isUnknown() && !file.isUnknown()

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

    private val cacheClassWithIdealSignature: MutableMap<EtsClassSignature, Maybe<EtsClass>> = hashMapOf()
    private val cacheMethodWithIdealSignature: MutableMap<EtsMethodSignature, Maybe<EtsMethod>> = hashMapOf()
    private val cachePartiallyMatchedCallees: MutableMap<EtsMethodSignature, List<EtsMethod>> = hashMapOf()

    private fun lookupClassWithIdealSignature(signature: EtsClassSignature): Maybe<EtsClass> {
        require(signature.isIdeal())

        if (signature in cacheClassWithIdealSignature) {
            return cacheClassWithIdealSignature.getValue(signature)
        }

        val matched = cp.classes
            .asSequence()
            .filter { it.signature == signature && it.signature.file == signature.file }
            .toList()
        if (matched.isEmpty()) {
            cacheClassWithIdealSignature[signature] = Maybe.none()
            return Maybe.none()
        } else {
            val s = matched.singleOrNull()
                ?: error("Multiple classes with the same signature: $matched")
            cacheClassWithIdealSignature[signature] = Maybe.some(s)
            return Maybe.some(s)
        }
    }

    override fun callees(node: EtsStmt): Sequence<EtsMethod> {
        val expr = node.callExpr ?: return emptySequence()
        val callee = expr.method

        // Note: the resolving code below expects that at least the current method signature is known.
        check(node.method.enclosingClass.isIdeal()) {
            "Incomplete signature in method: ${node.method}"
        }

        // Note: specific resolve for constructor:
        if (callee.name == "constructor") {
            if (!callee.enclosingClass.isIdeal()) {
                // Constructor signature is garbage. Sorry, can't do anything in such case.
                return emptySequence()
            }

            // Here, we assume that the constructor signature is ideal.
            check(callee.enclosingClass.isIdeal())

            val cls = lookupClassWithIdealSignature(callee.enclosingClass)
            if (cls.isSome) {
                return sequenceOf(cls.getOrThrow().ctor)
            } else {
                return emptySequence()
            }
        }

        // If the callee signature is ideal, resolve it directly:
        if (callee.enclosingClass.isIdeal()) {
            if (callee in cacheMethodWithIdealSignature) {
                val resolved = cacheMethodWithIdealSignature.getValue(callee)
                if (resolved.isSome) {
                    return sequenceOf(resolved.getOrThrow())
                } else {
                    return emptySequence()
                }
            }

            val cls = lookupClassWithIdealSignature(callee.enclosingClass)

            val resolved = run {
                if (cls.isNone) {
                    emptySequence()
                } else {
                    cls.getOrThrow().methods.asSequence().filter { it.name == callee.name }
                }
            }
            if (resolved.none()) {
                cacheMethodWithIdealSignature[callee] = Maybe.none()
                return emptySequence()
            }
            val r = resolved.singleOrNull()
                ?: error("Multiple methods with the same complete signature: ${resolved.toList()}")
            cacheMethodWithIdealSignature[callee] = Maybe.some(r)
            return sequenceOf(r)
        }

        // If the callee signature is not ideal, resolve it via a partial match...
        check(!callee.enclosingClass.isIdeal())

        val cls = lookupClassWithIdealSignature(node.method.enclosingClass).let {
            if (it.isNone) {
                error("Could not find the enclosing class: ${node.method.enclosingClass}")
            }
            it.getOrThrow()
        }

        // If the complete signature match failed,
        // try to find the unique not-the-same neighbour method in the same class:
        val neighbors = cls.methods
            .asSequence()
            .filter { it.name == callee.name }
            .filterNot { it.name == node.method.name }
            .toList()
        if (neighbors.isNotEmpty()) {
            val s = neighbors.singleOrNull()
                ?: error("Multiple methods with the same name: $neighbors")
            cachePartiallyMatchedCallees[callee] = listOf(s)
            return sequenceOf(s)
        }

        // NOTE: cache lookup MUST be performed AFTER trying to match the neighbour!
        if (callee in cachePartiallyMatchedCallees) {
            return cachePartiallyMatchedCallees.getValue(callee).asSequence()
        }

        // If the neighbour match failed,
        // try to *uniquely* resolve the callee via a partial signature match:
        val resolved = cp.classes
            .asSequence()
            .filter { compareClassSignatures(it.signature, callee.enclosingClass) != ComparisonResult.NotEqual }
            // Note: exclude current class:
            .filterNot { compareClassSignatures(it.signature, node.method.enclosingClass) != ComparisonResult.NotEqual }
            // Note: omit constructors!
            .flatMap { it.methods.asSequence() }
            .filter { it.name == callee.name }
            .toList()
        if (resolved.isEmpty()) {
            cachePartiallyMatchedCallees[callee] = emptyList()
            return emptySequence()
        }
        val r = resolved.singleOrNull() ?: run {
            logger.warn { "Multiple methods with the same partial signature '${callee}': $resolved" }
            cachePartiallyMatchedCallees[callee] = emptyList()
            return emptySequence()
        }
        cachePartiallyMatchedCallees[callee] = listOf(r)
        return sequenceOf(r)
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
