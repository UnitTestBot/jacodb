package org.utbot.jcdb.impl.cfg.util

import org.utbot.jcdb.api.JcRawInst
import org.utbot.jcdb.api.cfg.DefaultJcRawInstVisitor

class InstructionFilter(val predicate: (JcRawInst) -> Boolean) : DefaultJcRawInstVisitor<Boolean> {
    override val defaultInstHandler: (JcRawInst) -> Boolean
        get() = { predicate(it) }
}
