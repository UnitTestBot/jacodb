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
import info.leadinglight.jdot.impl.Util
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.cfg.JcAddExpr
import org.jacodb.api.cfg.JcAndExpr
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcArrayAccess
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcBasicBlock
import org.jacodb.api.cfg.JcBlockGraph
import org.jacodb.api.cfg.JcBool
import org.jacodb.api.cfg.JcByte
import org.jacodb.api.cfg.JcCallInst
import org.jacodb.api.cfg.JcCastExpr
import org.jacodb.api.cfg.JcCatchInst
import org.jacodb.api.cfg.JcChar
import org.jacodb.api.cfg.JcClassConstant
import org.jacodb.api.cfg.JcCmpExpr
import org.jacodb.api.cfg.JcCmpgExpr
import org.jacodb.api.cfg.JcCmplExpr
import org.jacodb.api.cfg.JcDivExpr
import org.jacodb.api.cfg.JcDouble
import org.jacodb.api.cfg.JcDynamicCallExpr
import org.jacodb.api.cfg.JcEnterMonitorInst
import org.jacodb.api.cfg.JcEqExpr
import org.jacodb.api.cfg.JcExitMonitorInst
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcExprVisitor
import org.jacodb.api.cfg.JcFieldRef
import org.jacodb.api.cfg.JcFloat
import org.jacodb.api.cfg.JcGeExpr
import org.jacodb.api.cfg.JcGotoInst
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcGtExpr
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstVisitor
import org.jacodb.api.cfg.JcInstanceOfExpr
import org.jacodb.api.cfg.JcInt
import org.jacodb.api.cfg.JcLambdaExpr
import org.jacodb.api.cfg.JcLeExpr
import org.jacodb.api.cfg.JcLengthExpr
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcLong
import org.jacodb.api.cfg.JcLtExpr
import org.jacodb.api.cfg.JcMethodConstant
import org.jacodb.api.cfg.JcMulExpr
import org.jacodb.api.cfg.JcNegExpr
import org.jacodb.api.cfg.JcNeqExpr
import org.jacodb.api.cfg.JcNewArrayExpr
import org.jacodb.api.cfg.JcNewExpr
import org.jacodb.api.cfg.JcNullConstant
import org.jacodb.api.cfg.JcOrExpr
import org.jacodb.api.cfg.JcPhiExpr
import org.jacodb.api.cfg.JcRemExpr
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcShlExpr
import org.jacodb.api.cfg.JcShort
import org.jacodb.api.cfg.JcShrExpr
import org.jacodb.api.cfg.JcSpecialCallExpr
import org.jacodb.api.cfg.JcStaticCallExpr
import org.jacodb.api.cfg.JcStringConstant
import org.jacodb.api.cfg.JcSubExpr
import org.jacodb.api.cfg.JcSwitchInst
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcThrowInst
import org.jacodb.api.cfg.JcUshrExpr
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.cfg.JcXorExpr
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.api.ext.toType
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

fun JcGraph.view(dotCmd: String, viewerCmd: String, viewCatchConnections: Boolean = false) {
    Util.sh(arrayOf(viewerCmd, "file://${toFile(dotCmd, viewCatchConnections)}"))
}

fun JcGraph.toFile(dotCmd: String, viewCatchConnections: Boolean = false, file: File? = null): Path {
    Graph.setDefaultCmd(dotCmd)

    val graph = Graph("jcGraph")

    val nodes = mutableMapOf<JcInst, Node>()
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


fun JcBlockGraph.view(dotCmd: String, viewerCmd: String) {
    Util.sh(arrayOf(viewerCmd, "file://${toFile(dotCmd)}"))
}

fun JcBlockGraph.toFile(dotCmd: String, file: File? = null): Path {
    Graph.setDefaultCmd(dotCmd)

    val graph = Graph("jcGraph")

    val nodes = mutableMapOf<JcBasicBlock, Node>()
    for ((index, block) in basicBlocks.withIndex()) {
        val node = Node("$index")
            .setShape(Shape.box)
            .setLabel(instructions(block).joinToString("") { "$it\\l" }.replace("\"", "\\\"").replace("\n", "\\n"))
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

fun JcGraph.apply(visitor: JcInstVisitor<Unit>): JcGraph {
    instructions.forEach { it.accept(visitor) }
    return this
}

fun <R, E, T : JcInstVisitor<E>> JcGraph.applyAndGet(visitor: T, getter: (T) -> R): R {
    instructions.forEach { it.accept(visitor) }
    return getter(visitor)
}

fun <T> JcGraph.collect(visitor: JcInstVisitor<T>): Collection<T> {
    return instructions.map { it.accept(visitor) }
}


fun <R, E, T : JcInstVisitor<E>> JcInst.applyAndGet(visitor: T, getter: (T) -> R): R {
    this.accept(visitor)
    return getter(visitor)
}

fun <R, E, T : JcExprVisitor<E>> JcExpr.applyAndGet(visitor: T, getter: (T) -> R): R {
    this.accept(visitor)
    return getter(visitor)
}

/**
 * Returns a list of possible thrown exceptions for any given instruction or expression (types of exceptions
 * are determined from JVM bytecode specification). For method calls it returns:
 * - all the declared checked exception types
 * - 'java.lang.Throwable' for any potential unchecked types
 */
class JcExceptionResolver(val classpath: JcClasspath) : JcInstVisitor<List<JcClassType>>, JcExprVisitor<List<JcClassType>> {
    private val throwableType = classpath.findTypeOrNull<Throwable>() as JcClassType
    private val nullPointerExceptionType = classpath.findTypeOrNull<NullPointerException>() as JcClassType
    private val arithmeticExceptionType = classpath.findTypeOrNull<ArithmeticException>() as JcClassType
    override fun visitJcAssignInst(inst: JcAssignInst): List<JcClassType> {
        return inst.lhv.accept(this) + inst.rhv.accept(this)
    }

    override fun visitJcEnterMonitorInst(inst: JcEnterMonitorInst): List<JcClassType> {
        return listOf(nullPointerExceptionType)
    }

    override fun visitJcExitMonitorInst(inst: JcExitMonitorInst): List<JcClassType> {
        return listOf(nullPointerExceptionType)
    }

    override fun visitJcCallInst(inst: JcCallInst): List<JcClassType> {
        return inst.callExpr.accept(this)
    }

    override fun visitJcReturnInst(inst: JcReturnInst): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcThrowInst(inst: JcThrowInst): List<JcClassType> {
        return listOf(inst.throwable.type as JcClassType, nullPointerExceptionType)
    }

    override fun visitJcCatchInst(inst: JcCatchInst): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcGotoInst(inst: JcGotoInst): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcIfInst(inst: JcIfInst): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcSwitchInst(inst: JcSwitchInst): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcAddExpr(expr: JcAddExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcAndExpr(expr: JcAndExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcCmpExpr(expr: JcCmpExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcCmpgExpr(expr: JcCmpgExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcCmplExpr(expr: JcCmplExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcDivExpr(expr: JcDivExpr): List<JcClassType> {
        return listOf(arithmeticExceptionType)
    }

    override fun visitJcMulExpr(expr: JcMulExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcEqExpr(expr: JcEqExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcNeqExpr(expr: JcNeqExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcGeExpr(expr: JcGeExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcGtExpr(expr: JcGtExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcLeExpr(expr: JcLeExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcLtExpr(expr: JcLtExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcOrExpr(expr: JcOrExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcRemExpr(expr: JcRemExpr): List<JcClassType> {
        return listOf(arithmeticExceptionType)
    }

    override fun visitJcShlExpr(expr: JcShlExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcShrExpr(expr: JcShrExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcSubExpr(expr: JcSubExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcUshrExpr(expr: JcUshrExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcXorExpr(expr: JcXorExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcLengthExpr(expr: JcLengthExpr): List<JcClassType> {
        return listOf(nullPointerExceptionType)
    }

    override fun visitJcNegExpr(expr: JcNegExpr): List<JcClassType> {
        return emptyList()
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

    override fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcLambdaExpr(expr: JcLambdaExpr): List<JcClassType> {
        return buildList {
            add(throwableType)
            addAll(expr.method.exceptions.map { it.toType() })
        }
    }

    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): List<JcClassType> {
        return listOf(throwableType)
    }

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): List<JcClassType> {
        return buildList {
            add(throwableType)
            add(nullPointerExceptionType)
            addAll(expr.method.exceptions.map { it.toType() })
        }
    }

    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): List<JcClassType> {
        return buildList {
            add(throwableType)
            addAll(expr.method.exceptions.map { it.toType() })
        }
    }

    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): List<JcClassType> {
        return buildList {
            add(throwableType)
            add(nullPointerExceptionType)
            addAll(expr.method.exceptions.map { it.toType() })
        }
    }

    override fun visitJcThis(value: JcThis): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcArgument(value: JcArgument): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcLocalVar(value: JcLocalVar): List<JcClassType> {
        return emptyList()
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

    override fun visitJcBool(value: JcBool): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcByte(value: JcByte): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcChar(value: JcChar): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcShort(value: JcShort): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcInt(value: JcInt): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcLong(value: JcLong): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcFloat(value: JcFloat): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcDouble(value: JcDouble): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcNullConstant(value: JcNullConstant): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcStringConstant(value: JcStringConstant): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcClassConstant(value: JcClassConstant): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcMethodConstant(value: JcMethodConstant): List<JcClassType> {
        return emptyList()
    }

    override fun visitJcPhiExpr(value: JcPhiExpr): List<JcClassType> {
        return emptyList()
    }
}
