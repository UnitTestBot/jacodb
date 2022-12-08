package org.utbot.jcdb.api.cfg

import org.utbot.jcdb.api.JcMethod

interface JcRawInstList {
    val instructions: List<JcRawInst>
    val size: Int
    val indices: IntRange
    val lastIndex: Int

    operator fun get(index: Int): JcRawInst
    fun getOrNull(index: Int): JcRawInst?
    fun iterator(): Iterator<JcRawInst>
    fun insertBefore(inst: JcRawInst, vararg newInstructions: JcRawInst)
    fun insertBefore(inst: JcRawInst, newInstructions: Collection<JcRawInst>)
    fun insertAfter(inst: JcRawInst, vararg newInstructions: JcRawInst)
    fun insertAfter(inst: JcRawInst, newInstructions: Collection<JcRawInst>)
    fun remove(inst: JcRawInst): Boolean
    fun removeAll(inst: Collection<JcRawInst>): Boolean
    fun graph(method: JcMethod): JcGraph
}