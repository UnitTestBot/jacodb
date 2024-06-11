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

package org.jacodb.impl.analysis.impl

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.cfg.BsmStringArg
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCatchInst
import org.jacodb.api.jvm.cfg.JcDynamicCallExpr
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcInstVisitor
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcStaticCallExpr
import org.jacodb.api.jvm.cfg.JcStringConstant
import org.jacodb.api.jvm.cfg.JcSwitchInst
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.cfg.values
import org.jacodb.api.jvm.ext.autoboxIfNeeded
import org.jacodb.api.jvm.ext.findTypeOrNull
import org.jacodb.impl.cfg.JcInstListImpl
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.jacodb.impl.cfg.methodRef
import kotlin.collections.set

class StringConcatSimplifierTransformer(
    classpath: JcClasspath,
    private val list: JcInstList<JcInst>,
) : JcInstVisitor.Default<JcInst> {

    override fun defaultVisitJcInst(inst: JcInst): JcInst {
        return inst
    }

    private val instructionReplacements = mutableMapOf<JcInst, JcInst>()
    private val instructions = mutableListOf<JcInst>()
    private val catchReplacements = mutableMapOf<JcInst, MutableList<JcInst>>()
    private val instructionIndices = mutableMapOf<JcInst, Int>()

    private val stringType = classpath.findTypeOrNull<String>() as JcClassType

    private var localCounter = list
        .flatMap { it.values.filterIsInstance<JcLocalVar>() }
        .maxOfOrNull { it.index }?.plus(1) ?: 0

    fun transform(): JcInstList<JcInst> {
        var changed = false
        for (inst in list) {
            if (inst is JcAssignInst) {
                val lhv = inst.lhv
                val rhv = inst.rhv

                if (rhv is JcDynamicCallExpr && rhv.callSiteMethodName == "makeConcatWithConstants") {

                    val (first, second) = when {
                        rhv.callSiteArgs.size == 2 -> rhv.callSiteArgs
                        rhv.callSiteArgs.size == 1 && rhv.bsmArgs.size == 1 && rhv.bsmArgs[0] is BsmStringArg -> listOf(
                            rhv.callSiteArgs[0],
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

                    val concatMethod = stringType.declaredMethods.first {
                        it.name == "concat" && it.parameters.size == 1 && it.parameters.first().type == stringType
                    }
                    val methodRef = VirtualMethodRefImpl.of(stringType, concatMethod)
                    val newConcatExpr = JcVirtualCallExpr(methodRef, firstStr, listOf(secondStr))
                    result += JcAssignInst(inst.location, lhv, newConcatExpr)
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

        if (!changed) return list

        /**
         * after we changed the instruction list, we need to examine new instruction list and
         * remap all the old JcInstRef's to new ones
         */
        instructionIndices.putAll(instructions.indices.map { instructions[it] to it })
        return JcInstListImpl(instructions.map { it.accept(this) })
    }

    private fun stringify(inst: JcInst, value: JcValue, instList: MutableList<JcInst>): JcValue {
        return when {
            PredefinedPrimitives.matches(value.type.typeName) -> {
                val boxedType = value.type.autoboxIfNeeded() as JcClassType
                val method = boxedType.methods.first {
                    it.name == "toString" && it.parameters.size == 1 && it.parameters.first().type == value.type
                }
                val toStringExpr = JcStaticCallExpr(method.methodRef(), listOf(value))
                val assignment = JcLocalVar(localCounter++, "${value}String", stringType)
                instList += JcAssignInst(inst.location, assignment, toStringExpr)
                assignment
            }

            value.type == stringType -> value
            else -> {
                val boxedType = value.type.autoboxIfNeeded() as JcClassType
                val method = boxedType.methods.first {
                    it.name == "toString" && it.parameters.isEmpty()
                }
                val methodRef = VirtualMethodRefImpl.of(boxedType, method)
                val toStringExpr = JcVirtualCallExpr(methodRef, value, emptyList())
                val assignment = JcLocalVar(localCounter++, "${value}String", stringType)
                instList += JcAssignInst(inst.location, assignment, toStringExpr)
                assignment
            }
        }
    }

    private fun indexOf(instRef: JcInstRef) = JcInstRef(
        instructionIndices[instructionReplacements.getOrDefault(list.get(instRef.index), list.get(instRef.index))] ?: -1
    )

    private fun indicesOf(instRef: JcInstRef): List<JcInstRef> {
        val index = list.get(instRef.index)
        return catchReplacements.getOrDefault(index, listOf(index)).map {
            JcInstRef(instructions.indexOf(it))

        }
    }

    override fun visitJcCatchInst(inst: JcCatchInst): JcInst = JcCatchInst(
        inst.location,
        inst.throwable,
        inst.throwableTypes,
        inst.throwers.flatMap { indicesOf(it) }
    )

    override fun visitJcGotoInst(inst: JcGotoInst): JcInst = JcGotoInst(inst.location, indexOf(inst.target))

    override fun visitJcIfInst(inst: JcIfInst): JcInst = JcIfInst(
        inst.location,
        inst.condition,
        indexOf(inst.trueBranch),
        indexOf(inst.falseBranch)
    )

    override fun visitJcSwitchInst(inst: JcSwitchInst): JcInst = JcSwitchInst(
        inst.location,
        inst.key,
        inst.branches.mapValues { indexOf(it.value) },
        indexOf(inst.default)
    )
}
