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

package org.jacodb.ets.dsl

data class Program(
    val nodes: List<Node>,
) {
    fun toText(indent: Int = 2): String {
        val lines = mutableListOf<String>()

        fun process(nodes: List<Node>, currentIndent: Int = 0) {
            fun line(line: String) {
                lines += " ".repeat(currentIndent) + line
            }

            for (node in nodes) {
                when (node) {
                    is Nop -> line("nop")
                    is Assign -> line("${node.target} := ${node.expr}")
                    is Return -> line("return ${node.expr}")
                    is If -> {
                        line("if (${node.condition}) {")
                        process(node.thenBranch, currentIndent + indent)
                        if (node.elseBranch.isNotEmpty()) {
                            line("} else {")
                            process(node.elseBranch, currentIndent + indent)
                        }
                        line("}")
                    }

                    is Label -> line("label ${node.name}")
                    is Goto -> line("goto ${node.targetLabel}")

                    is CustomEts -> line("???")
                }
            }
        }

        process(nodes)

        return lines.joinToString("\n")
    }
}

fun program(block: ProgramBuilder.() -> Unit): Program {
    val builder = ProgramBuilderImpl()
    builder.block()
    return builder.build()
}
