package org.jacodb.analysis.alias

import org.jacodb.analysis.alias.apg.AccessGraph
import org.jacodb.api.cfg.JcInst

data class AliasResult(
    val aliases: List<AccessGraph>,
    val allocationSites: List<JcInst>
)
