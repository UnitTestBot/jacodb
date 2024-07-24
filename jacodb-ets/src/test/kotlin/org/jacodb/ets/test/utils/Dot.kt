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

package org.jacodb.ets.test.utils

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.utils.dumpDot
import java.io.File

/**
 * Visualize classes and methods in [EtsFileDto].
 */
object DumpEtsFileDtoToDot {
    private const val BASE_PATH = "/etsir/samples"
    private const val NAME = "basic" // <-- change it
    private const val DOT_PATH = "ir.dot"

    @JvmStatic
    fun main(args: Array<String>) {
        val path = "$BASE_PATH/${NAME}.ts.json"
        val etsFileDto: EtsFileDto = loadEtsFileDtoFromResource(path)

        println("EtsFileDto '${etsFileDto.name}':")
        etsFileDto.classes.forEach { clazz ->
            println("= CLASS '${clazz.signature}':")
            println("  superClass = '${clazz.superClassName}'")
            println("  typeParameters = ${clazz.typeParameters}")
            println("  modifiers = ${clazz.modifiers}")
            println("  fields: ${clazz.fields.size}")
            clazz.fields.forEach { field ->
                println("  - FIELD '${field.signature}'")
                println("    typeParameters = ${field.typeParameters}")
                println("    modifiers = ${field.modifiers}")
                println("    isOptional = ${field.isOptional}")
                println("    isDefinitelyAssigned = ${field.isDefinitelyAssigned}")
            }
            println("  methods: ${clazz.methods.size}")
            clazz.methods.forEach { method ->
                println("  - METHOD '${method.signature}':")
                println("    locals = ${method.body.locals}")
                println("    typeParameters = ${method.typeParameters}")
                println("    blocks: ${method.body.cfg.blocks.size}")
                method.body.cfg.blocks.forEach { block ->
                    println("    - BLOCK ${block.id} with ${block.stmts.size} statements:")
                    block.stmts.forEachIndexed { i, inst ->
                        println("      ${i + 1}. $inst")
                    }
                }
            }
        }

        println("Rendering EtsFileDto to DOT...")
        render(DOT_PATH) { file ->
            etsFileDto.dumpDot(file)
        }
    }
}

object DumpEtsFileToDot {
    private const val BASE_PATH = "/etsir/samples"
    private const val NAME = "basic" // <-- change it
    private const val DOT_PATH = "ets.dot"

    @JvmStatic
    fun main(args: Array<String>) {
        val path = "$BASE_PATH/${NAME}.ts.json"
        val etsFile = loadEtsFileFromResource(path)

        println("EtsFile '${etsFile.name}':")
        etsFile.classes.forEach { clazz ->
            println("= CLASS '${clazz.signature}':")
            println("  superClass = '${clazz.superClass}'")
            println("  fields: ${clazz.fields.size}")
            clazz.fields.forEach { field ->
                println("  - FIELD '${field.signature}'")
            }
            println("  constructor = '${clazz.ctor.signature}'")
            println("    stmts: ${clazz.ctor.cfg.stmts.size}")
            clazz.ctor.cfg.stmts.forEachIndexed { i, stmt ->
                println("    ${i + 1}. $stmt")
            }
            println("  methods: ${clazz.methods.size}")
            clazz.methods.forEach { method ->
                println("  - METHOD '${method.signature}':")
                println("    locals = ${method.localsCount}")
                println("    stmts: ${method.cfg.stmts.size}")
                method.cfg.stmts.forEachIndexed { i, stmt ->
                    println("    ${i + 1}. $stmt")
                }
            }
        }

        println("Rendering EtsFile to DOT...")
        render(DOT_PATH) { file ->
            etsFile.dumpDot(file)
        }
    }
}

private fun render(path: String, dump: (File) -> Unit) {
    val dotFile = File(path)
    dump(dotFile)
    println("Generated DOT file: ${dotFile.absolutePath}")
    for (format in listOf("pdf")) {
        val formatFile = dotFile.resolveSibling(dotFile.nameWithoutExtension + ".$format")
        val p = Runtime.getRuntime().exec("dot -T$format $dotFile -o $formatFile")
        p.waitFor()
        print(p.inputStream.bufferedReader().readText())
        print(p.errorStream.bufferedReader().readText())
        println("Generated ${format.uppercase()} file: ${formatFile.absolutePath}")
    }
}
