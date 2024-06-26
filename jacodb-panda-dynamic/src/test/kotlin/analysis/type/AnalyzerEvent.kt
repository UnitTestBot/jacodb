package analysis.type

import org.jacodb.panda.dynamic.ets.model.EtsMethod

sealed interface AnalyzerEvent

data class ForwardSummaryAnalyzerEvent(
    val method: EtsMethod,
    val initialFact: ForwardTypeDomainFact,
    val exitFact: ForwardTypeDomainFact
) : AnalyzerEvent

data class BackwardSummaryAnalyzerEvent(
    val method: EtsMethod,
    val initialFact: BackwardTypeDomainFact,
    val exitFact: BackwardTypeDomainFact
) : AnalyzerEvent
