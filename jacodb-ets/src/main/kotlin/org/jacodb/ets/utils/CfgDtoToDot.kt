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

import org.jacodb.ets.dto.AliasTypeDto
import org.jacodb.ets.dto.AnyTypeDto
import org.jacodb.ets.dto.ArrayRefDto
import org.jacodb.ets.dto.ArrayTypeDto
import org.jacodb.ets.dto.AssignStmtDto
import org.jacodb.ets.dto.AwaitExprDto
import org.jacodb.ets.dto.BinaryOperationDto
import org.jacodb.ets.dto.BooleanTypeDto
import org.jacodb.ets.dto.CallStmtDto
import org.jacodb.ets.dto.CastExprDto
import org.jacodb.ets.dto.CaughtExceptionRefDto
import org.jacodb.ets.dto.CfgDto
import org.jacodb.ets.dto.ClassTypeDto
import org.jacodb.ets.dto.ClosureFieldRefDto
import org.jacodb.ets.dto.ConstantDto
import org.jacodb.ets.dto.DeleteExprDto
import org.jacodb.ets.dto.EnumValueTypeDto
import org.jacodb.ets.dto.FunctionTypeDto
import org.jacodb.ets.dto.GenericTypeDto
import org.jacodb.ets.dto.GlobalRefDto
import org.jacodb.ets.dto.IfStmtDto
import org.jacodb.ets.dto.InstanceCallExprDto
import org.jacodb.ets.dto.InstanceFieldRefDto
import org.jacodb.ets.dto.InstanceOfExprDto
import org.jacodb.ets.dto.IntersectionTypeDto
import org.jacodb.ets.dto.LexicalEnvTypeDto
import org.jacodb.ets.dto.LiteralTypeDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.NeverTypeDto
import org.jacodb.ets.dto.NewArrayExprDto
import org.jacodb.ets.dto.NewExprDto
import org.jacodb.ets.dto.NopStmtDto
import org.jacodb.ets.dto.NullTypeDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.ParameterRefDto
import org.jacodb.ets.dto.PrimitiveLiteralDto
import org.jacodb.ets.dto.PtrCallExprDto
import org.jacodb.ets.dto.RawStmtDto
import org.jacodb.ets.dto.RawTypeDto
import org.jacodb.ets.dto.RawValueDto
import org.jacodb.ets.dto.RelationOperationDto
import org.jacodb.ets.dto.ReturnStmtDto
import org.jacodb.ets.dto.ReturnVoidStmtDto
import org.jacodb.ets.dto.StaticCallExprDto
import org.jacodb.ets.dto.StaticFieldRefDto
import org.jacodb.ets.dto.StmtDto
import org.jacodb.ets.dto.StringTypeDto
import org.jacodb.ets.dto.ThisRefDto
import org.jacodb.ets.dto.ThrowStmtDto
import org.jacodb.ets.dto.TupleTypeDto
import org.jacodb.ets.dto.TypeDto
import org.jacodb.ets.dto.TypeOfExprDto
import org.jacodb.ets.dto.UnaryOperationDto
import org.jacodb.ets.dto.UnclearReferenceTypeDto
import org.jacodb.ets.dto.UndefinedTypeDto
import org.jacodb.ets.dto.UnionTypeDto
import org.jacodb.ets.dto.UnknownTypeDto
import org.jacodb.ets.dto.ValueDto
import org.jacodb.ets.dto.VoidTypeDto
import org.jacodb.ets.dto.YieldExprDto

/**
 * Convert a CFG DTO to DOT format for visualization with Graphviz.
 *
 * This is a secondary conversion function for DTO-based CFGs.
 * For the main ETS model, use [org.jacodb.ets.model.EtsBlockCfg.toDot] instead.
 *
 * @param useHtml if true, uses HTML table format for blocks; if false, uses plain text
 * @return DOT graph string
 */
fun CfgDto.toDot(
    useHtml: Boolean = true,
): String {
    val lines = mutableListOf<String>()
    lines += "digraph cfg {"
    lines += "  node [shape=${if (useHtml) "none" else "rect"} fontname=\"monospace\"]"

    // Nodes
    for (block in blocks) {
        if (useHtml) {
            val s = block.stmts.joinToString("") {
                it.toDotLabel().htmlEncode() + "<br/>"
            }
            val h = "<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">" +
                "<tr><td>" + "<b>Block #${block.id}</b>" + "</td></tr>" +
                "<tr><td balign=\"left\">" + s + "</td></tr>" +
                "</table>"
            lines += "  ${block.id} [label=<${h}>]"
        } else {
            val s = block.stmts.joinToString("") { it.toDotLabel() + "\\l" }
            lines += "  ${block.id} [label=\"Block #${block.id}\\n$s\"]"
        }
    }

    // Edges
    for (block in blocks) {
        val succs = block.successors
        if (succs.isEmpty()) continue
        if (succs.size == 1) {
            lines += "  ${block.id} -> ${succs.single()}"
        } else {
            check(succs.size == 2) { "Block ${block.id} has ${succs.size} successors, expected 1 or 2" }
            val (trueBranch, falseBranch) = succs
            lines += "  ${block.id} -> $trueBranch [label=\"true\"]"
            lines += "  ${block.id} -> $falseBranch [label=\"false\"]"
        }
    }

    lines += "}"
    return lines.joinToString("\n")
}

/**
 * Convert a statement DTO to a DOT label string.
 *
 * This is a simple conversion that uses custom formatting for values.
 * For better formatting, consider converting to ETS model first and using
 * [org.jacodb.ets.model.EtsStmt.toDotLabel] instead.
 */
private fun StmtDto.toDotLabel(): String {
    val label = when (this) {
        is NopStmtDto -> "nop"
        is AssignStmtDto -> "${left.toDotLabel()} := ${right.toDotLabel()}"
        is ReturnVoidStmtDto -> "return"
        is ReturnStmtDto -> "return ${arg.toDotLabel()}"
        is ThrowStmtDto -> "throw ${arg.toDotLabel()}"
        is IfStmtDto -> "if (${condition.toDotLabel()})"
        is CallStmtDto -> "call ${expr.toDotLabel()}"
        is RawStmtDto -> "raw $kind"
    }
    return label.replace("\"", "\\\"")
}

/**
 * Convert a value DTO to a DOT label string.
 *
 * Provides concise formatting for various value types without verbose data class toString().
 */
private fun ValueDto.toDotLabel(): String {
    return when (this) {
        // Immediates
        is LocalDto -> name
        is ConstantDto -> when (type) {
            is StringTypeDto -> "\"$value\""
            else -> value
        }

        // References
        is ThisRefDto -> "this"
        is ParameterRefDto -> "arg$index"
        is CaughtExceptionRefDto -> "@caught ${type.toDotLabel()}"
        is GlobalRefDto -> name
        is ClosureFieldRefDto -> "${base.name}.$fieldName"
        is ArrayRefDto -> "${array.toDotLabel()}[${index.toDotLabel()}]"
        is InstanceFieldRefDto -> "${instance.toDotLabel()}.${field.name}"
        is StaticFieldRefDto -> "${field.declaringClass.name}.${field.name}"

        // Expressions
        is NewExprDto -> "new ${classType.toDotLabel()}"
        is NewArrayExprDto -> "new Array<${elementType.toDotLabel()}>(${size.toDotLabel()})"
        is DeleteExprDto -> "delete ${arg.toDotLabel()}"
        is AwaitExprDto -> "await ${arg.toDotLabel()}"
        is YieldExprDto -> "yield ${arg.toDotLabel()}"
        is TypeOfExprDto -> "typeof ${arg.toDotLabel()}"
        is InstanceOfExprDto -> "${arg.toDotLabel()} instanceof ${checkType.toDotLabel()}"
        is CastExprDto -> "${arg.toDotLabel()} as ${type.toDotLabel()}"

        // Unary operations
        is UnaryOperationDto -> "$op ${arg.toDotLabel()}"

        // Binary operations
        is BinaryOperationDto -> "${left.toDotLabel()} $op ${right.toDotLabel()}"
        is RelationOperationDto -> "${left.toDotLabel()} $op ${right.toDotLabel()}"

        // Call expressions
        is InstanceCallExprDto -> {
            val argsStr = args.joinToString(", ") { it.toDotLabel() }
            "call ${instance.toDotLabel()}.${method.name}($argsStr)"
        }

        is StaticCallExprDto -> {
            val argsStr = args.joinToString(", ") { it.toDotLabel() }
            "static ${method.declaringClass.name}.${method.name}($argsStr)"
        }

        is PtrCallExprDto -> {
            val argsStr = args.joinToString(", ") { it.toDotLabel() }
            "ptr ${ptr.toDotLabel()}.${method.name}($argsStr)"
        }

        // Raw value
        is RawValueDto -> "raw:$kind"
    }
}

/**
 * Convert a type DTO to a concise DOT label string.
 */
private fun TypeDto.toDotLabel(): String {
    return when (this) {
        // Primitive types
        is AnyTypeDto -> "any"
        is UnknownTypeDto -> "unknown"
        is UndefinedTypeDto -> "undefined"
        is NullTypeDto -> "null"
        is VoidTypeDto -> "void"
        is NeverTypeDto -> "never"
        is NumberTypeDto -> "number"
        is StringTypeDto -> "string"
        is BooleanTypeDto -> "boolean"

        is LiteralTypeDto -> when (literal) {
            is PrimitiveLiteralDto.StringLiteral -> "\"${literal.value}\""
            is PrimitiveLiteralDto.NumberLiteral -> literal.value.toString()
            is PrimitiveLiteralDto.BooleanLiteral -> literal.value.toString()
        }

        // Complex types
        is ClassTypeDto -> if (typeParameters.isNotEmpty()) {
            val generics = typeParameters.joinToString(", ") { it.toDotLabel() }
            "${signature.name}<$generics>"
        } else {
            signature.name
        }

        is UnclearReferenceTypeDto -> if (typeParameters.isNotEmpty()) {
            val generics = typeParameters.joinToString(", ") { it.toDotLabel() }
            "$name<$generics>"
        } else {
            name
        }

        is ArrayTypeDto -> "${elementType.toDotLabel()}${"[]".repeat(dimensions)}"
        is TupleTypeDto -> types.joinToString(", ", "[", "]") { it.toDotLabel() }

        is UnionTypeDto -> types.joinToString(" | ") {
            val s = it.toDotLabel()
            if (it is UnionTypeDto || it is IntersectionTypeDto) "($s)" else s
        }

        is IntersectionTypeDto -> types.joinToString(" & ") {
            val s = it.toDotLabel()
            if (it is UnionTypeDto || it is IntersectionTypeDto) "($s)" else s
        }

        is FunctionTypeDto -> {
            val params = signature.parameters.joinToString(", ") { it.type.toDotLabel() }
            "($params) => ${signature.returnType.toDotLabel()}"
        }

        // Special types
        is GenericTypeDto -> name
        is AliasTypeDto -> "$name=${originalType.toDotLabel()}"
        is EnumValueTypeDto -> "${signature.name}${name?.let { ".$it" } ?: ""}"
        is LexicalEnvTypeDto -> "<${method.name}>(${closures.joinToString { it.name }})"

        // Raw type
        is RawTypeDto -> "raw:$kind"
    }
}
