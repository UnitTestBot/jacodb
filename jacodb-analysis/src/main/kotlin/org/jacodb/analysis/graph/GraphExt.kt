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

package org.jacodb.analysis.graph

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Color
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.impl.Util
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

fun JcApplicationGraph.dfs(
    node: JcInst,
    method: JcMethod,
    visited: MutableSet<JcInst>,
) {
    if (visited.add(node)) {
        for (next in successors(node)) {
            if (next.location.method == method) {
                dfs(next, method, visited)
            }
        }
    }
}

fun JcApplicationGraph.view(method: JcMethod, dotCmd: String, viewerCmd: String) {
    Util.sh(arrayOf(viewerCmd, "file://${toFile(method, dotCmd)}"))
}

fun JcApplicationGraph.toFile(
    method: JcMethod,
    dotCmd: String,
    file: File? = null,
): Path {
    Graph.setDefaultCmd(dotCmd)

    val graph = Graph("jcApplicationGraph")
    graph.setBgColor(Color.X11.transparent)
    graph.setFontSize(12.0)
    graph.setFontName("monospace")

    val allInstructions: MutableSet<JcInst> = hashSetOf()
    for (start in entryPoints(method)) {
        dfs(start, method, allInstructions)
    }

    val nodes = mutableMapOf<JcInst, Node>()
    for ((index, inst) in allInstructions.withIndex()) {
        val node = Node("$index")
            .setShape(Shape.box)
            .setLabel(inst.toString().replace("\"", "\\\""))
            .setFontSize(12.0)
        nodes[inst] = node
        graph.addNode(node)
    }

    for ((inst, node) in nodes) {
        for (next in successors(inst)) {
            if (next in nodes) {
                val edge = Edge(node.name, nodes[next]!!.name)
                graph.addEdge(edge)
            }
        }
    }

    val outFile = graph.dot2file("svg")
    val newFile = "${outFile.removeSuffix("out")}svg"
    val resultingFile = file?.toPath() ?: File(newFile).toPath()
    Files.move(File(outFile).toPath(), resultingFile)
    return resultingFile
}
