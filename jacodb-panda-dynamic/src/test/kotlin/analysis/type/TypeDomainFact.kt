package analysis.type

sealed interface BackwardTypeDomainFact {
    object Zero : BackwardTypeDomainFact

    data class TypedVariable(val variable: AccessPathBase, val type: EtsTypeFact) : BackwardTypeDomainFact
}

sealed interface ForwardTypeDomainFact {
    object Zero : ForwardTypeDomainFact

    data class TypedVariable(val variable: AccessPath, val type: EtsTypeFact) : ForwardTypeDomainFact
}
