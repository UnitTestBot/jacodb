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

package ets

import antlr.HuimpleLexer
import antlr.HuimpleParser
import antlr.HuimpleParserBaseListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThreeAddressCodeParserTest {

    private class CustomListener : HuimpleParserBaseListener() {
        var variableDeclarations: MutableList<String> = mutableListOf()
        var functionDeclarations: MutableList<String> = mutableListOf()
        var classDeclarations: MutableList<String> = mutableListOf()
        var propertyDeclarations: MutableList<String> = mutableListOf()
        var methodDeclarations: MutableList<String> = mutableListOf()
        var interfaceDeclarations: MutableList<String> = mutableListOf()
        var enumDeclarations: MutableList<String> = mutableListOf()
        val codeStatements = mutableListOf<String>()

        override fun exitVariableDeclaration(ctx: HuimpleParser.VariableDeclarationContext) {
            variableDeclarations.add(ctx.Identifier().text)
        }

        override fun exitFunctionDeclaration(ctx: HuimpleParser.FunctionDeclarationContext) {
            functionDeclarations.add(ctx.Identifier().text)
        }

        override fun exitClassDeclaration(ctx: HuimpleParser.ClassDeclarationContext) {
            classDeclarations.add(ctx.Identifier().text)
        }

        override fun exitPropertyDeclaration(ctx: HuimpleParser.PropertyDeclarationContext) {
            propertyDeclarations.add(ctx.Identifier().text)
        }

        override fun exitMethodDeclaration(ctx: HuimpleParser.MethodDeclarationContext) {
            methodDeclarations.add(ctx.Identifier().text)
        }

        override fun exitInterfaceDeclaration(ctx: HuimpleParser.InterfaceDeclarationContext) {
            interfaceDeclarations.add(ctx.Identifier().text)
        }

        override fun exitEnumDeclaration(ctx: HuimpleParser.EnumDeclarationContext) {
            enumDeclarations.add(ctx.Identifier().text)
        }

        override fun enterExpressionStatement(ctx: HuimpleParser.ExpressionStatementContext) {
            codeStatements.add(ctx.text)
        }

        override fun enterIfStatement(ctx: HuimpleParser.IfStatementContext) {
            codeStatements.add(ctx.text)
        }

        override fun enterIterationStatement(ctx: HuimpleParser.IterationStatementContext) {
            codeStatements.add(ctx.text)
        }

        override fun enterSwitchStatement(ctx: HuimpleParser.SwitchStatementContext) {
            codeStatements.add(ctx.text)
        }

        override fun enterReturnStatement(ctx: HuimpleParser.ReturnStatementContext) {
            codeStatements.add(ctx.text)
        }

        override fun enterContinueStatement(ctx: HuimpleParser.ContinueStatementContext) {
            codeStatements.add(ctx.text)
        }

        override fun enterBreakStatement(ctx: HuimpleParser.BreakStatementContext) {
            codeStatements.add(ctx.text)
        }
    }

    private fun parse(code: String): HuimpleParser.ProgramContext {
        val lexer = HuimpleLexer(CharStreams.fromString(code))
        val tokens = CommonTokenStream(lexer)
        val parser = HuimpleParser(tokens)
        return parser.program()
    }

    private fun walk(tree: HuimpleParser.ProgramContext): CustomListener {
        val listener = CustomListener()
        ParseTreeWalker.DEFAULT.walk(listener, tree)
        return listener
    }

    @Test
    fun testSimpleVariableDeclaration() {
        val code = "let x: number;"
        val program = parse(code)
        val listener = walk(program)
        assertEquals(listOf("x"), listener.variableDeclarations)
    }

    @Test
    fun testFunctionDeclaration() {
        val code = """
            function add(a: number, b: number): number {
                return a + b;
            }
        """.trimIndent()
        val program = parse(code)
        val listener = walk(program)
        assertEquals(listOf("add"), listener.functionDeclarations)
    }

    @Test
    fun testClassDeclaration() {
        val code = """
            class Person {
                public name: string;
                constructor(name: string) {
                    this.name = name;
                }
                public getName(): string {
                    return this.name;
                }
            }
        """.trimIndent()
        val program = parse(code)
        val listener = walk(program)
        assertEquals(listOf("Person"), listener.classDeclarations)
        assertEquals(listOf("name"), listener.propertyDeclarations)
        assertEquals(listOf("getName"), listener.methodDeclarations)
    }

    @Test
    fun testInterfaceDeclaration() {
        val code = """
            interface Shape {
                draw(): void;
            }
        """.trimIndent()
        val program = parse(code)
        val listener = walk(program)
        assertEquals(listOf("Shape"), listener.interfaceDeclarations)
    }

    @Test
    fun testEnumDeclaration() {
        val code = """
            enum Direction {
                Up,
                Down,
                Left,
                Right
            }
        """.trimIndent()
        val program = parse(code)
        val listener = walk(program)
        assertEquals(listOf("Direction"), listener.enumDeclarations)
    }

    @Test
    fun testComplexProgram() {
        val code = """
            let x: number;
            let y: number;
            let c: Calculator;
            let r: number;
            
            x = 10;
            y = 20;
            c = new Calculator();
            r = c.calculate(x, y);
            
            function add(a: number, b: number): number {
                return a + b;
            }
            class Calculator {
                public result: number;
                constructor() {
                    this.result = 0;
                }
                public calculate(a: number, b: number) {
                    this.result = add(a, b);
                }
            }
            export { Calculator as Calc };
        """.trimIndent()
        val program = parse(code)
        val listener = walk(program)
        assertEquals(listOf("x", "y", "c", "r"), listener.variableDeclarations)
        assertEquals(listOf("add"), listener.functionDeclarations)
        assertEquals(listOf("Calculator"), listener.classDeclarations)
        assertTrue(listener.interfaceDeclarations.isEmpty())
        assertTrue(listener.enumDeclarations.isEmpty())
        assertEquals(
            // Note: whitespaces are skipped by the parser
            listOf(
                "x=10;",
                "y=20;",
                "c=newCalculator();",
                "r=c.calculate(x,y);",
                "returna+b;",
                "this.result=0;",
                "this.result=add(a,b);"
            ),
            listener.codeStatements
        )
    }

    @Test
    fun testHelloWorldProgram() {
        val code = """
        let someClass: any;
        let m: any;
        let ${'$'}temp1: any;
        let ${'$'}temp2: any;
        let x: number;
        let ${'$'}temp3: any;
        let soo: any;
        let ${'$'}temp4: any;

        someClass = SomeClass;
        ${'$'}temp2 = "Hello, world";
        ${'$'}temp1 = new someClass(${'$'}temp2);
        m = ${'$'}temp1;
        ${'$'}temp3 = 1;
        x = ${'$'}temp3;
        ${'$'}temp4 = 123;
        soo = ${'$'}temp4;
        forLoopTest();
        test();
    """.trimIndent()
        val tree = parse(code)
        val listener = walk(tree)
        assertEquals(
            listOf(
                "someClass",
                "m",
                "\$temp1",
                "\$temp2",
                "x",
                "\$temp3",
                "soo",
                "\$temp4"
            ), listener.variableDeclarations
        )
        assertTrue(listener.functionDeclarations.isEmpty())
        assertTrue(listener.classDeclarations.isEmpty())
        assertTrue(listener.interfaceDeclarations.isEmpty())
        assertTrue(listener.enumDeclarations.isEmpty())
        assertTrue(listener.codeStatements.isNotEmpty())
    }
}
