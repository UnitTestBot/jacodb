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

package org.jacodb.panda.dynamic.ets.graph

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Color
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.impl.Util
import org.jacodb.impl.cfg.graphs.GraphDominators
import org.jacodb.panda.dynamic.ets.base.EtsIfStmt
import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.base.EtsTerminatingStmt
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class EtsCfg(
    val stmts: List<EtsStmt>,
    private val successorMap: Map<EtsStmt, List<EtsStmt>>,
) : EtsBytecodeGraph<EtsStmt> {

    private val predecessorMap: Map<EtsStmt, Set<EtsStmt>> by lazy {
        val map: MutableMap<EtsStmt, MutableSet<EtsStmt>> = hashMapOf()
        for ((stmt, nexts) in successorMap) {
            for (next in nexts) {
                map.getOrPut(next) { hashSetOf() } += stmt
            }
        }
        map
    }

    override fun throwers(node: EtsStmt): Set<EtsStmt> {
        TODO("Current version of IR does not contain try catch blocks")
    }

    override fun catchers(node: EtsStmt): Set<EtsStmt> {
        TODO("Current version of IR does not contain try catch blocks")
    }

    override val instructions: List<EtsStmt>
        get() = stmts

    override val entries: List<EtsStmt>
        get() = listOf(stmts.first())

    override val exits: List<EtsTerminatingStmt>
        get() = instructions.filterIsInstance<EtsTerminatingStmt>()

    override fun successors(node: EtsStmt): Set<EtsStmt> {
        return successorMap[node]!!.toSet()
    }

    override fun predecessors(node: EtsStmt): Set<EtsStmt> {
        return predecessorMap[node]!!
    }
}

fun EtsCfg.findDominators(): GraphDominators<EtsStmt> {
    return GraphDominators(this).also { it.find() }
}

fun EtsCfg.view(dotCmd: String, viewerCmd: String, viewCatchConnections: Boolean = false) {
    Util.sh(arrayOf(viewerCmd, "file://${toFile(dotCmd, viewCatchConnections)}"))
}

fun EtsCfg.toFile(dotCmd: String, viewCatchConnections: Boolean = false, file: File? = null): Path {
    Graph.setDefaultCmd(dotCmd)

    val graph = Graph("etsCfg")

    val nodes = mutableMapOf<EtsStmt, Node>()
    for ((index, inst) in instructions.withIndex()) {
        val node = Node("$index")
            .setShape(Shape.box)
            .setLabel(inst.toString().replace("\"", "\\\""))
            .setFontSize(12.0)
        nodes[inst] = node
        graph.addNode(node)
    }

    graph.setBgColor(Color.X11.transparent)
    graph.setFontSize(12.0)
    graph.setFontName("Fira Mono")

    for ((inst, node) in nodes) {
        when (inst) {
            is EtsIfStmt -> {
                val successors = successors(inst).toList()
                check(successors.size == 2)
                graph.addEdge(
                    Edge(node.name, nodes[successors[0]]!!.name)
                        .also {
                            it.setLabel("false")
                        }
                )
                graph.addEdge(
                    Edge(node.name, nodes[successors[1]]!!.name)
                        .also {
                            it.setLabel("true")
                        }
                )
            }

            else -> for (successor in successors(inst)) {
                graph.addEdge(Edge(node.name, nodes[successor]!!.name))
            }
        }
        if (viewCatchConnections) {
            // TODO: uncomment when `catchers` are properly implemented
            // for (catcher in catchers(inst)) {
            //     graph.addEdge(Edge(node.name, nodes[catcher]!!.name).also {
            //         // it.setLabel("catch ${catcher.throwable.type}")
            //         it.setLabel("catch")
            //         it.setStyle(Style.Edge.dashed)
            //     })
            // }
        }
    }

    val outFile = graph.dot2file("svg")
    val newFile = "${outFile.removeSuffix(".out")}.svg"
    val resultingFile = file?.toPath() ?: File(newFile).toPath()
    Files.move(File(outFile).toPath(), resultingFile)
    return resultingFile
}
