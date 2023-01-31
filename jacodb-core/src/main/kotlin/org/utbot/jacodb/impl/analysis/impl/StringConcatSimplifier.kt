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

package org.utbot.jacodb.impl.analysis.impl

import org.utbot.jacodb.api.JcClassType
import org.utbot.jacodb.api.PredefinedPrimitives
import org.utbot.jacodb.api.cfg.*
import org.utbot.jacodb.api.ext.autoboxIfNeeded
import org.utbot.jacodb.api.ext.findTypeOrNull
import org.utbot.jacodb.impl.cfg.JcGraphImpl
import kotlin.collections.set


class StringConcatSimplifier(
    val jcGraph: JcGraphImpl
) : DefaultJcInstVisitor<JcInst> {
    override val defaultInstHandler: (JcInst) -> JcInst
        get() = { it }
    private val instructionReplacements = mutableMapOf<JcInst, JcInst>()
    private val instructions = mutableListOf<JcInst>()
    private val catchReplacements = mutableMapOf<JcInst, MutableList<JcInst>>()
    private val instructionIndices = mutableMapOf<JcInst, Int>()

    private val stringType = jcGraph.classpath.findTypeOrNull<String>() as JcClassType

    fun build(): JcGraphImpl {
        var changed = false
        for (inst in jcGraph) {
            if (inst is JcAssignInst) {
                val lhv = inst.lhv
                val rhv = inst.rhv

                if (rhv is JcDynamicCallExpr && rhv.callCiteMethodName == "makeConcatWithConstants") {

                    val (first, second) = when {
                        rhv.callCiteArgs.size == 2 -> rhv.callCiteArgs
                        rhv.callCiteArgs.size == 1 && rhv.bsmArgs.size == 1 && rhv.bsmArgs[0] is BsmStringArg -> listOf(
                            rhv.callCiteArgs[0],
                            JcStringConstant((rhv.bsmArgs[0] as BsmStringArg).value, stringType)
                        )

                        else -> {
                            instructions += inst
                            continue
                        }
                    }
                    changed = true

                    val result = mutableListOf<JcInst>()
                    val firstStr = stringify(inst, first, result)
                    val secondStr = stringify(inst, second, result)

                    val concatMethod = stringType.methods.first {
                        it.name == "concat" && it.parameters.size == 1 && it.parameters.first().type == stringType
                    }
                    val newConcatExpr = JcVirtualCallExpr(concatMethod, firstStr, listOf(secondStr))
                    result += JcAssignInst(inst.lineNumber, lhv, newConcatExpr)
                    instructionReplacements[inst] = result.first()
                    catchReplacements[inst] = result
                    instructions += result
                } else {
                    instructions += inst
                }
            } else {
                instructions += inst
            }
        }

        if (!changed) return jcGraph

        /**
         * after we changed the instruction list, we need to examine new instruction list and
         * remap all the old JcInstRef's to new ones
         */
        instructionIndices.putAll(instructions.indices.map { instructions[it] to it })
        val mappedInstructions = instructions.map { it.accept(this) }
        return JcGraphImpl(jcGraph.method, mappedInstructions)
    }

    private fun stringify(inst: JcInst, value: JcValue, instList: MutableList<JcInst>): JcValue {
        val cp = jcGraph.classpath
        val stringType = cp.findTypeOrNull<String>()!!
        return when {
            PredefinedPrimitives.matches(value.type.typeName) -> {
                val boxedType = value.type.autoboxIfNeeded() as JcClassType
                val method = boxedType.methods.first {
                    it.name == "toString" && it.parameters.size == 1 && it.parameters.first().type == value.type
                }
                val toStringExpr = JcStaticCallExpr(method, listOf(value))
                val assignment = JcLocalVar("${value}String", stringType)
                instList += JcAssignInst(inst.lineNumber, assignment, toStringExpr)
                assignment
            }

            value.type == stringType -> value
            else -> {
                val boxedType = value.type.autoboxIfNeeded() as JcClassType
                val method = boxedType.methods.first {
                    it.name == "toString" && it.parameters.isEmpty()
                }
                val toStringExpr = JcVirtualCallExpr(method, value, emptyList())
                val assignment = JcLocalVar("${value}String", stringType)
                instList += JcAssignInst(inst.lineNumber, assignment, toStringExpr)
                assignment
            }
        }
    }

    private fun indexOf(instRef: JcInstRef) = JcInstRef(
        instructionIndices[instructionReplacements.getOrDefault(jcGraph.inst(instRef), jcGraph.inst(instRef))] ?: -1
    )

    private fun indicesOf(instRef: JcInstRef) =
        catchReplacements.getOrDefault(jcGraph.inst(instRef), listOf(jcGraph.inst(instRef))).map {
            JcInstRef(instructions.indexOf(it))
        }

    override fun visitJcCatchInst(inst: JcCatchInst): JcInst = JcCatchInst(
        inst.lineNumber,
        inst.throwable,
        inst.throwers.flatMap { indicesOf(it) }
    )

    override fun visitJcGotoInst(inst: JcGotoInst): JcInst = JcGotoInst(inst.lineNumber, indexOf(inst.target))

    override fun visitJcIfInst(inst: JcIfInst): JcInst = JcIfInst(
        inst.lineNumber,
        inst.condition,
        indexOf(inst.trueBranch),
        indexOf(inst.falseBranch)
    )

    override fun visitJcSwitchInst(inst: JcSwitchInst): JcInst = JcSwitchInst(
        inst.lineNumber,
        inst.key,
        inst.branches.mapValues { indexOf(it.value) },
        indexOf(inst.default)
    )
}
