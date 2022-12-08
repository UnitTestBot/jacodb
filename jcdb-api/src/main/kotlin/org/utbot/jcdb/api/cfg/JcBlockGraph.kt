package org.utbot.jcdb.api.cfg


/**
 * Basic block represents a list of instructions that:
 * - guaranteed to execute one after other during normal control flow
 * (i.e. no exceptions thrown)
 * - all have the same exception handlers (i.e. `jcGraph.catchers(inst)`
 * returns the same result for all instructions of the basic block)
 *
 * Because of the current implementation of basic block API, block is *not*
 * guaranteed to end with a terminating (i.e. `JcTerminatingInst` or `JcBranchingInst`) instruction.
 * However, any terminating instruction is guaranteed to be the last instruction of a basic block.
 */
class JcBasicBlock(val start: JcInstRef, val end: JcInstRef)

interface JcBlockGraph: Iterable<JcBasicBlock> {
    val jcGraph: JcGraph
    val basicBlocks: List<JcBasicBlock>
    val entry: JcBasicBlock
    val exits: List<JcBasicBlock>
    fun instructions(block: JcBasicBlock): List<JcInst>

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    fun predecessors(block: JcBasicBlock): Set<JcBasicBlock>
    fun successors(block: JcBasicBlock): Set<JcBasicBlock>

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     */
    fun catchers(block: JcBasicBlock): Set<JcBasicBlock>
    fun throwers(block: JcBasicBlock): Set<JcBasicBlock>
}