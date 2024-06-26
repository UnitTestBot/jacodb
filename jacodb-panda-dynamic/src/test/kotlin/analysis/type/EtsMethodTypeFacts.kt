package analysis.type

import org.jacodb.panda.dynamic.ets.model.EtsMethod

data class EtsMethodTypeFacts(
    val method: EtsMethod,
    val types: Map<AccessPathBase, EtsTypeFact>,
)
