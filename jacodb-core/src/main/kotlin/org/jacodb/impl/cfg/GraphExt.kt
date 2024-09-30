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

package org.jacodb.impl.cfg

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Color
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.enums.Style
import info.leadinglight.jdot.impl.Util
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBasicBlock
import org.jacodb.api.jvm.cfg.JcBlockGraph
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcDivExpr
import org.jacodb.api.jvm.cfg.JcDynamicCallExpr
import org.jacodb.api.jvm.cfg.JcExitMonitorInst
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcExprVisitor
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcGraph
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstVisitor
import org.jacodb.api.jvm.cfg.JcLambdaExpr
import org.jacodb.api.jvm.cfg.JcLengthExpr
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcNewExpr
import org.jacodb.api.jvm.cfg.JcRemExpr
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcStaticCallExpr
import org.jacodb.api.jvm.cfg.JcSwitchInst
import org.jacodb.api.jvm.cfg.JcThrowInst
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.findTypeOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

private const val DEFAULT_DOT_CMD = "dot"

fun JcGraph.view(
    viewerCmd: String = if (System.getProperty("os.name").lowercase().contains("windows")) "start" else "xdg-open",
    dotCmd: String = DEFAULT_DOT_CMD,
    viewCatchConnections: Boolean = false,
) {
    val path = toFile(null, dotCmd, viewCatchConnections)
    Util.sh(arrayOf(viewerCmd, "file://$path"))
}

fun JcGraph.toFile(
    file: File? = null,
    dotCmd: String = DEFAULT_DOT_CMD,
    viewCatchConnections: Boolean = false,
): Path {
    Graph.setDefaultCmd(dotCmd)

    val graph = Graph("jcGraph")

    val nodes = mutableMapOf<JcInst, Node>()
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
            is JcGotoInst -> for (successor in successors(inst)) {
                graph.addEdge(Edge(node.name, nodes[successor]!!.name))
            }

            is JcIfInst -> {
                graph.addEdge(
                    Edge(node.name, nodes[inst(inst.trueBranch)]!!.name)
                        .also {
                            it.setLabel("true")
                        }
                )
                graph.addEdge(
                    Edge(node.name, nodes[inst(inst.falseBranch)]!!.name)
                        .also {
                            it.setLabel("false")
                        }
                )
            }

            is JcSwitchInst -> {
                for ((key, branch) in inst.branches) {
                    graph.addEdge(
                        Edge(node.name, nodes[inst(branch)]!!.name)
                            .also {
                                it.setLabel("$key")
                            }
                    )
                }
                graph.addEdge(
                    Edge(node.name, nodes[inst(inst.default)]!!.name)
                        .also {
                            it.setLabel("else")
                        }
                )
            }

            else -> for (successor in successors(inst)) {
                graph.addEdge(Edge(node.name, nodes[successor]!!.name))
            }
        }
        if (viewCatchConnections) {
            for (catcher in catchers(inst)) {
                graph.addEdge(Edge(node.name, nodes[catcher]!!.name).also {
                    it.setLabel("catch ${catcher.throwable.type}")
                    it.setStyle(Style.Edge.dashed)
                })
            }
        }
    }

    val outFile = graph.dot2file("svg")
    val newFile = "${outFile.removeSuffix("out")}svg"
    val resultingFile = file?.toPath() ?: File(newFile).toPath()
    Files.move(File(outFile).toPath(), resultingFile)
    return resultingFile
}

fun JcBlockGraph.view(
    viewerCmd: String,
    dotCmd: String = DEFAULT_DOT_CMD,
) {
    val path = toFile(null, dotCmd = dotCmd)
    Util.sh(arrayOf(viewerCmd, "file://$path"))
}

fun JcBlockGraph.toFile(
    file: File? = null,
    dotCmd: String = DEFAULT_DOT_CMD,
): Path {
    Graph.setDefaultCmd(dotCmd)

    val graph = Graph("jcGraph")

    val nodes = mutableMapOf<JcBasicBlock, Node>()
    for ((index, block) in instructions.withIndex()) {
        val label = instructions(block)
            .joinToString("") { "$it\\l" }
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        val node = Node("$index")
            .setShape(Shape.box)
            .setLabel(label)
            .setFontSize(12.0)
        nodes[block] = node
        graph.addNode(node)
    }

    graph.setBgColor(Color.X11.transparent)
    graph.setFontSize(12.0)
    graph.setFontName("Fira Mono")

    for ((block, node) in nodes) {
        val terminatingInst = instructions(block).last()
        val successors = successors(block)
        when (terminatingInst) {
            is JcGotoInst -> for (successor in successors) {
                graph.addEdge(Edge(node.name, nodes[successor]!!.name))
            }

            is JcIfInst -> {
                graph.addEdge(
                    Edge(node.name, nodes[successors.first { it.start == terminatingInst.trueBranch }]!!.name)
                        .also {
                            it.setLabel("true")
                        }
                )
                graph.addEdge(
                    Edge(node.name, nodes[successors.first { it.start == terminatingInst.falseBranch }]!!.name)
                        .also {
                            it.setLabel("false")
                        }
                )
            }

            is JcSwitchInst -> {
                for ((key, branch) in terminatingInst.branches) {
                    graph.addEdge(
                        Edge(node.name, nodes[successors.first { it.start == branch }]!!.name)
                            .also {
                                it.setLabel("$key")
                            }
                    )
                }
                graph.addEdge(
                    Edge(node.name, nodes[successors.first { it.start == terminatingInst.default }]!!.name)
                        .also {
                            it.setLabel("else")
                        }
                )
            }

            else -> for (successor in successors(block)) {
                graph.addEdge(Edge(node.name, nodes[successor]!!.name))
            }
        }
    }

    val outFile = graph.dot2file("svg")
    val newFile = "${outFile.removeSuffix("out")}svg"
    val resultingFile = file?.toPath() ?: File(newFile).toPath()
    Files.move(File(outFile).toPath(), resultingFile)
    return resultingFile
}

/**
 * Returns a list of possible thrown exceptions for any given instruction or expression (types of exceptions
 * are determined from JVM bytecode specification). For method calls it returns:
 * - all the declared checked exception types
 * - 'java.lang.Throwable' for any potential unchecked types
 */
open class JcExceptionResolver(
    val classpath: JcClasspath,
) : JcExprVisitor.Default<List<JcClassType>>,
    JcInstVisitor.Default<List<JcClassType>> {

    private val throwableType = classpath.findTypeOrNull<Throwable>() as JcClassType
    private val errorType = classpath.findTypeOrNull<Error>() as JcClassType
    private val runtimeExceptionType = classpath.findTypeOrNull<RuntimeException>() as JcClassType
    private val nullPointerExceptionType = classpath.findTypeOrNull<NullPointerException>() as JcClassType
    private val arithmeticExceptionType = classpath.findTypeOrNull<ArithmeticException>() as JcClassType

    override fun defaultVisitJcExpr(expr: JcExpr): List<JcClassType> {
        return emptyList()
    }

    override fun defaultVisitJcInst(inst: JcInst): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcAssignInst(inst: JcAssignInst): List<JcClassType> {
        return inst.lhv.accept(this) + inst.rhv.accept(this)
    }

    override fun visitJcExitMonitorInst(inst: JcExitMonitorInst): List<JcClassType> {
        return listOf(nullPointerExceptionType)
    }

    override fun visitJcCallInst(inst: JcCallInst): List<JcClassType> {
        return inst.callExpr.accept(this)
    }

    override fun visitJcThrowInst(inst: JcThrowInst): List<JcClassType> {
        return listOf(inst.throwable.type as JcClassType, nullPointerExceptionType)
    }

    override fun visitJcDivExpr(expr: JcDivExpr): List<JcClassType> {
        return listOf(arithmeticExceptionType)
    }

    override fun visitJcRemExpr(expr: JcRemExpr): List<JcClassType> {
        return listOf(arithmeticExceptionType)
    }

    override fun visitJcLengthExpr(expr: JcLengthExpr): List<JcClassType> {
        return listOf(nullPointerExceptionType)
    }

    override fun visitJcCastExpr(expr: JcCastExpr): List<JcClassType> {
        return when {
            PredefinedPrimitives.matches(expr.type.typeName) -> emptyList()
            else -> listOf(classpath.findTypeOrNull<ClassCastException>() as JcClassType)
        }
    }

    override fun visitJcNewExpr(expr: JcNewExpr): List<JcClassType> {
        return listOf(classpath.findTypeOrNull<Error>() as JcClassType)
    }

    override fun visitJcNewArrayExpr(expr: JcNewArrayExpr): List<JcClassType> {
        return listOf(classpath.findTypeOrNull<NegativeArraySizeException>() as JcClassType)
    }

    override fun visitJcLambdaExpr(expr: JcLambdaExpr): List<JcClassType> {
        return buildList {
            add(runtimeExceptionType)
            add(errorType)
            addAll(expr.method.exceptions.thisOrThrowable())
        }
    }

    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): List<JcClassType> {
        return listOf(throwableType)
    }

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): List<JcClassType> {
        return buildList {
            add(runtimeExceptionType)
            add(errorType)
            addAll(expr.method.exceptions.thisOrThrowable())
        }
    }

    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): List<JcClassType> {
        return buildList {
            add(runtimeExceptionType)
            add(errorType)
            addAll(expr.method.exceptions.thisOrThrowable())
        }
    }

    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): List<JcClassType> {
        return buildList {
            add(runtimeExceptionType)
            add(errorType)
            addAll(expr.method.exceptions.thisOrThrowable())
        }
    }

    override fun visitJcFieldRef(value: JcFieldRef): List<JcClassType> {
        return listOf(nullPointerExceptionType)
    }

    override fun visitJcArrayAccess(value: JcArrayAccess): List<JcClassType> {
        return listOf(
            nullPointerExceptionType,
            classpath.findTypeOrNull<IndexOutOfBoundsException>() as JcClassType
        )
    }

    private fun <E> List<E>.thisOrThrowable(): Collection<JcClassType> {
        return map {
            when (it) {
                is JcClassType -> it
                else -> throwableType
            }
        }
    }

}
