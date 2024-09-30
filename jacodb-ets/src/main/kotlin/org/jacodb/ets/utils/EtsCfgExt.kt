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

package org.jacodb.ets.utils

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Color
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.impl.Util
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.graph.EtsCfg
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

private const val DEFAULT_DOT_CMD = "dot"

fun EtsCfg.view(
    viewerCmd: String = if (System.getProperty("os.name").startsWith("Windows")) "start" else "xdg-open",
    dotCmd: String = DEFAULT_DOT_CMD,
    viewCatchConnections: Boolean = true,
) {
    val path = toFile(null, dotCmd, viewCatchConnections)
    Util.sh(arrayOf(viewerCmd, "file://$path"))
}

fun EtsCfg.toFile(
    file: File? = null,
    dotCmd: String = DEFAULT_DOT_CMD,
    viewCatchConnections: Boolean = true,
): Path {
    Graph.setDefaultCmd(dotCmd)

    val graph = Graph("etsCfg")

    val nodes = mutableMapOf<EtsStmt, Node>()
    for ((index, inst) in instructions.withIndex()) {
        val label = inst.toString().replace("\"", "\\\"")
        val node = Node("$index")
            .setShape(Shape.box)
            .setLabel(label)
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
