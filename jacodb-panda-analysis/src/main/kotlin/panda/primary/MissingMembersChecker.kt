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

package panda.primary

import org.jacodb.panda.dynamic.api.*

private val logger = mu.KotlinLogging.logger {}

class MissingMembersChecker(val project: PandaProject) {
    val graph = PandaApplicationGraphImpl(project)

    companion object {
        val noMemberTypes = listOf(
            PandaUndefinedType::class,
            PandaArrayType::class,
            PandaPrimitiveType::class
        )
    }

    fun analyseOneCase(
        startMethods: List<String>
    ): List<PandaInst> {
        val methods = graph.project.classes.flatMap { it.methods }
        val typeMismatches = mutableListOf<PandaInst>()
        for (method in methods) {
            if (startMethods.contains(method.name)) {
                for (inst in method.instructions) {
                    val probablyMissingPropertyMembers = inst.recursiveOperands.mapNotNull { op ->
                        when (op) {
                            is PandaValueByInstance -> op
                            else -> null
                        }
                    }

                    probablyMissingPropertyMembers.forEach { member ->
                        if (noMemberTypes.any { it.isInstance(member.instance.type) }) {
                            logger.info { "Accessing member ${member.property} on instance ${member.instance} of ${member.instance.typeName} type (inst: $inst)" }
                            typeMismatches.add(inst)
                        } else if (member.instance is PandaNullConstant) {
                            logger.info { "Accessing member ${member.property} on instance ${member.instance} (inst: $inst)" }
                            typeMismatches.add(inst)
                        }
                    }

                    var callExpr: PandaInstanceCallExpr? = null

                    if (inst is PandaCallInst) {
                        if (inst.callExpr !is PandaInstanceCallExpr) {
                            TODO("Consider that case too")
                        }
                        callExpr = inst.callExpr as PandaInstanceCallExpr
                    }
                    if (inst is PandaAssignInst) {
                        inst.rhv.let { right ->
                            if (right is PandaCallExpr) {
                                when (right) {
                                    is PandaInstanceCallExpr -> callExpr = right
                                    else -> TODO("Consider that case too")
                                }
                            }
                        }
                    }
                    if (callExpr == null) {
                        continue
                    }
                    val callee = callExpr!!.method
                    val instance = callExpr!!.instance
                    if (noMemberTypes.any { it.isInstance(instance.type) }) {
                        logger.info { "Calling member ${callee.name} on instance $instance of ${instance.typeName} type (inst: $inst)" }
                        typeMismatches.add(inst)
                    } else if (instance is PandaNullConstant) {
                        logger.info { "Calling member ${callee.name} on instance $instance (inst: $inst)" }
                        typeMismatches.add(inst)
                    } else if (instance.type is PandaClassTypeImpl || instance is PandaLoadedValue) {
                        try {
                            // TODO(): get rid off adhoc
                            if (instance is PandaLoadedValue && instance.className == "console" && callee.name == "log") {
                                continue
                            }
                            // TODO: "callee.enclosingClass" is always non-null, BUT can be non-initialized (lateinit var), which will cause an exception in runtime
                            if (callee.enclosingClass_ != null) {
                                continue
                            }
                        } catch (e: UninitializedPropertyAccessException) { // simply means that IRParser cannot resolve a method
                            logger.info { "Calling member ${callee.name} on instance $instance that have no such a member (inst: $inst)" }
                            typeMismatches.add(inst)
                        }
                    }
                }
            }
        }
        return typeMismatches
    }
}
