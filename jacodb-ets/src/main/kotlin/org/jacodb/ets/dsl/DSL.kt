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

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.view

private fun main() {
    val prog = program {
        // i := arg(0)
        assign(local("i"), param(0))

        // if (i > 10)
        ifStmt(gt(local("i"), const(10))) {
            ifStmt(eq(local("i"), const(42))) {
                // if (i == 42) {
                //   return i
                ret(local("i"))
            }.elseIf(eq(local("i"), const(20))) {
                // } else if (i == 20) {
                //   return i
                ret(local("i"))
            }.`else` {
                // } else {
                //   i := 10
                assign(local("i"), const(10))
            }
            nop()
        }

        label("loop")
        ifStmt(gt(local("i"), const(0))) {
            // if (i > 0) {
            //   i := i - 1
            //   goto @loop
            assign(local("i"), sub(local("i"), const(1)))
            goto("loop")
        }.`else` {
            // } else {
            //   return i
            ret(local("i"))
        }

        // x.foo := 35
        customStmt { loc ->
            EtsAssignStmt(
                location = loc,
                lhv = EtsInstanceFieldRef(
                    instance = EtsLocal("x", EtsUnknownType),
                    field = EtsFieldSignature(
                        enclosingClass = EtsClassSignature.UNKNOWN,
                        name = "foo",
                        type = EtsUnknownType,
                    ),
                    type = EtsUnknownType
                ),
                rhv = EtsNumberConstant(35.0)
            )
        }

        ret(const(100)) // unreachable
    }

    val doView = false

    println("PROGRAM:")
    println("-----")
    println(prog.toText())
    println("-----")

    println("=== PROGRAM:")
    println(prog.toDot())
    if (doView) view(prog.toDot(), name = "program")

    val blockCfg = prog.toBlockCfg()
    println("=== BLOCK CFG:")
    println(blockCfg.toDot())
    if (doView) view(blockCfg.toDot(), name = "block")

    val linearCfg = blockCfg.linearize()
    println("=== LINEARIZED CFG:")
    println(linearCfg.toDot())
    if (doView) view(linearCfg.toDot(), name = "linear")
}
