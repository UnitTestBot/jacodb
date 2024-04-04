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

package org.jacodb.panda.dynamic.parser

import antlr.TypeScriptLexer
import antlr.TypeScriptParser
import antlr.TypeScriptParser.ClassDeclarationContext
import antlr.TypeScriptParserBaseListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaType
import java.net.URI
import java.nio.file.Paths

class TSParser(tsPath: URI) {

    private val program: TypeScriptParser.ProgramContext
    private val walker: ParseTreeWalker

    init {
        val lexer = TypeScriptLexer(CharStreams.fromPath(Paths.get(tsPath)))
        val tokenStream = CommonTokenStream(lexer)
        val parser = TypeScriptParser(tokenStream)
        program = parser.program()
        walker = ParseTreeWalker()
    }

    fun collectFunctions(): List<TSFunction> {
        val listener = Kek()
        walker.walk(listener, program)
        return listener.getFunctions()
    }

}

data class TSFunction(
    val name: String,
    val arguments: List<PandaType>,
    val returnType: PandaType,
    val containingClass: TSClass? = null
)

data class TSClass(
    val name: String,
    val scopeClass: TSClass? = null
)


class Kek : TypeScriptParserBaseListener() {

    private val funcs = mutableListOf<TSFunction>()

    fun getFunctions() = funcs.toList()

    override fun enterFunctionDeclaration(ctx: TypeScriptParser.FunctionDeclarationContext?) {
        ctx?.let { context ->
            val funcName = context.identifier()
            val callSignature = context.callSignature()
            val paramList = callSignature.parameterList()
            val returnType = callSignature.typeAnnotation()

            funcs.add(TSFunction(
                name = funcName.text,
                arguments = paramList?.parameter()?.mapParameters() ?: emptyList(),
                returnType = returnType.toPandaType(),
                containingClass = traceClasses(context)
            ))
        }
    }

    private fun traceClasses(node: RuleContext): TSClass? {
        var parent: RuleContext? = node.parent
        while (parent != null) {
            if (parent is ClassDeclarationContext) {
                return TSClass(
                    parent.identifier().text,
                    traceClasses(parent)
                )
            }
            parent = parent.parent
        }

        return TSClass(
            "GLOBAL",
            null
        )
    }

    private fun List<TypeScriptParser.ParameterContext>.mapParameters(): List<PandaType> = this.map { parameter ->
        parameter.requiredParameter()?.let { reqParam ->
            reqParam.typeAnnotation().toPandaType()
        } ?: parameter.optionalParameter().typeAnnotation().toPandaType()
    }

    fun TypeScriptParser.TypeAnnotationContext?.toPandaType(): PandaType = when (this?.type_()?.text) {
        null, "any" -> PandaAnyType
        "number" -> PandaNumberType
        "boolean" -> PandaBoolType
        else -> error("Unknown type: ${this.text}")
    }
}
