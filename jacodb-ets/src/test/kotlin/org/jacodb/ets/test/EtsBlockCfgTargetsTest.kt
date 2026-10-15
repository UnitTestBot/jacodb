package org.jacodb.ets.test

import org.jacodb.ets.dsl.BlockAssign
import org.jacodb.ets.dsl.BlockIf
import org.jacodb.ets.dsl.BlockReturn
import org.jacodb.ets.dsl.Assign
import org.jacodb.ets.dsl.ConstantBoolean
import org.jacodb.ets.dsl.ConstantInt
import org.jacodb.ets.dsl.Goto
import org.jacodb.ets.dsl.If
import org.jacodb.ets.dsl.Label
import org.jacodb.ets.dsl.Local
import org.jacodb.ets.dsl.Program
import org.jacodb.ets.dsl.Return
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.program
import org.jacodb.ets.dsl.toBlockCfg
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.toEtsBlockCfg
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EtsBlockCfgTargetsTest {
    @Test
    fun `forward goto resolves after all blocks are built`() {
        val cfg = program {
            goto("end")
            assign(local("unreachable"), const(1))
            label("end")
            ret(const(2))
        }.toBlockCfg()

        val returnBlock = cfg.blocks.single { it.statements.lastOrNull() is BlockReturn }

        assertEquals(listOf(returnBlock.id), cfg.successors[0])
        assertEquals(emptyList(), cfg.successors[returnBlock.id])
    }

    @Test
    fun `backward goto resolves target in the current block`() {
        val cfg = program {
            label("loop")
            assign(local("x"), const(1))
            assign(local("y"), const(2))
            goto("loop")
        }.toBlockCfg()

        assertEquals(2, cfg.blocks[0].statements.size)
        assertTrue(cfg.blocks[0].statements.all { it is BlockAssign })
        assertEquals(listOf(0), cfg.successors[0])
    }

    @Test
    fun `nested forward and backward targets preserve branch order`() {
        val cfg = program {
            ifStmt(const(true)) {
                goto("after")
            }.`else` {
                label("again")
                assign(local("x"), const(1))
                goto("again")
            }
            label("after")
            ret(const(2))
        }.toBlockCfg()

        val ifBlock = cfg.blocks.first()
        val elseBlock = cfg.blocks.single { block ->
            block.statements.any { it is BlockAssign }
        }
        val returnBlock = cfg.blocks.single { it.statements.lastOrNull() is BlockReturn }
        val thenBlockId = cfg.successors.getValue(ifBlock.id).first()

        assertEquals(elseBlock.id, cfg.successors.getValue(ifBlock.id)[1])
        assertEquals(listOf(returnBlock.id), cfg.successors[thenBlockId])
        assertEquals(listOf(elseBlock.id), cfg.successors[elseBlock.id])
    }

    @Test
    fun `goto may target a label after an unreachable return`() {
        val cfg = program {
            goto("after")
            ret(const(0))
            label("after")
            ret(const(1))
        }.toBlockCfg()

        val targetBlock = cfg.blocks.last { it.statements.lastOrNull() is BlockReturn }

        assertEquals(listOf(targetBlock.id), cfg.successors[0])
    }

    @Test
    fun `multiple gotos resolve to the same target`() {
        val cfg = program {
            ifStmt(const(true)) {
                goto("done")
            }.`else` {
                goto("done")
            }
            label("done")
            ret(const(0))
        }.toBlockCfg()

        val branchTargets = cfg.successors.getValue(0)
        val targetBlock = cfg.blocks.single { it.statements.lastOrNull() is BlockReturn }

        assertEquals(2, branchTargets.size)
        assertEquals(listOf(targetBlock.id), cfg.successors[branchTargets[0]])
        assertEquals(listOf(targetBlock.id), cfg.successors[branchTargets[1]])
    }

    @Test
    fun `label on singleton nop retains following assignment`() {
        val cfg = program {
            goto("start")
            label("start")
            nop()
            assign(local("x"), const(1))
            nop()
            ret(local("x"))
        }.toBlockCfg()

        val targetBlockId = cfg.successors.getValue(0).single()
        val targetStatements = cfg.blocks[targetBlockId].statements

        assertEquals(1, targetStatements.count { it is BlockAssign })
        assertTrue(targetStatements.last() is BlockReturn)
    }

    @Test
    fun `repeated node instance keeps labeled occurrence within one list`() {
        val shared = Assign(target = Local(name = "x"), expr = ConstantInt(value = 1))
        val prog = Program(
            nodes = listOf(
                Goto(targetLabel = "start"),
                Label(name = "start"),
                shared,
                shared,
                Return(expr = Local(name = "x")),
            ),
        )

        val cfg = prog.toBlockCfg()
        val targetBlockId = cfg.successors.getValue(0).single()

        assertEquals(2, cfg.blocks[targetBlockId].statements.count { it is BlockAssign })
        assertTrue(cfg.blocks[targetBlockId].statements.last() is BlockReturn)
    }

    @Test
    fun `repeated node instance in branches keeps labeled true occurrence`() {
        val shared = Assign(target = Local(name = "x"), expr = ConstantInt(value = 1))
        val prog = Program(
            nodes = listOf(
                Goto(targetLabel = "then"),
                If(
                    condition = ConstantBoolean(value = true),
                    thenBranch = listOf(Label(name = "then"), shared),
                    elseBranch = listOf(shared),
                ),
                Return(expr = Local(name = "x")),
            ),
        )

        val cfg = prog.toBlockCfg()
        val ifBlockId = cfg.blocks.single { it.statements.lastOrNull() is BlockIf }.id
        val branchIds = cfg.successors.getValue(ifBlockId)

        assertEquals(branchIds[0], cfg.successors.getValue(0).single())
        assertTrue(branchIds[0] != branchIds[1])
    }

    @Test
    fun `empty if branches retain distinct ETS successors`() {
        val cfg = program {
            ifStmt(local("condition")) {}
            ret(const(0))
        }.toBlockCfg()
        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = EtsClassSignature.UNKNOWN,
                name = "emptyBranches",
                parameters = emptyList(),
                returnType = EtsUnknownType,
            ),
        )

        val branchIds = cfg.successors.getValue(0)
        val continuationId = cfg.blocks.single { it.statements.lastOrNull() is BlockReturn }.id
        val etsCfg = cfg.toEtsBlockCfg(method)
        val ifStmt = etsCfg.blocks.first().statements.single() as EtsIfStmt
        val etsBranches = etsCfg.successors(ifStmt)

        assertEquals(2, branchIds.size)
        assertTrue(branchIds[0] != branchIds[1])
        assertEquals(listOf(continuationId), cfg.successors[branchIds[0]])
        assertEquals(listOf(continuationId), cfg.successors[branchIds[1]])
        assertEquals(2, etsBranches.size)
        assertTrue(etsBranches.all { it is EtsNopStmt })
    }

    @Test
    fun `unknown and duplicate labels remain invalid`() {
        val unknown = program {
            goto("missing")
        }
        val duplicate = program {
            label("same")
            ret(const(0))
            label("same")
            ret(const(1))
        }

        val unknownError = assertFailsWith<IllegalStateException> { unknown.toBlockCfg() }
        val duplicateError = assertFailsWith<IllegalStateException> { duplicate.toBlockCfg() }

        assertTrue(unknownError.message.orEmpty().contains("Unknown label"))
        assertTrue(duplicateError.message.orEmpty().contains("Duplicate label"))
    }
}
