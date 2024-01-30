package org.jacodb.analysis.alias.flow

import org.jacodb.analysis.alias.AliasEvent
import org.jacodb.analysis.alias.apg.AccessGraph
import org.jacodb.analysis.ifds2.FlowFunctions

abstract class AliasFlowFunctions : FlowFunctions<AccessGraph> {
    val events = ArrayDeque<AliasEvent>()
}