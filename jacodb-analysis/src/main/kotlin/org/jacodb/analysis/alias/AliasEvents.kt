package org.jacodb.analysis.alias

import org.jacodb.analysis.alias.apg.AccessGraph
import org.jacodb.analysis.ifds2.Edge
import org.jacodb.api.JcField
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcNewExpr
import org.jacodb.api.cfg.JcReturnInst

interface AliasEvent

data class SummaryEdge(
    val edge: Edge<AccessGraph>,
    val edgeType: Type
) : AliasEvent {
    enum class Type {
        FUNCTION_SUMMARY, ALLOCATION, TRANSITIVE, PARAMETER
    }

    override fun toString(): String = edge.toString()
}

sealed class Poi : AliasEvent {
    abstract val stmt: JcInst
}

data class AllocationSite(
    override val stmt: JcAssignInst,
    val lhs: JcLocalVar,
    val newExpr: JcNewExpr,
) : Poi()

data class FieldRead(
    override val stmt: JcAssignInst,
    val rhs: JcLocal,
    val field: JcField,
    val accessGraph: AccessGraph,
) : Poi()

data class AliasOnCall(
    override val stmt: JcInst,
    val beginStatement: JcInst,
    val accessGraph: AccessGraph
) : Poi()

data class AliasOnReturn(
    override val stmt: JcReturnInst,
    val returnSite: JcInst,
    val accessGraph: AccessGraph,
) : Poi()

data class FieldWrite(
    override val stmt: JcAssignInst,
    val lhs: JcLocal,
    val field: JcField,
    val accessGraph: AccessGraph,
) : Poi()

data class Turnover(
    override val stmt: JcAssignInst
) : Poi()