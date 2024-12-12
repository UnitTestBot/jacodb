package org.jacodb.taint.configuration.v2

import kotlinx.serialization.Serializable

sealed interface SerializedRule {
    val function: SerializedFunctionNameMatcher
    val signature: SerializedSignatureMatcher?
    val overrides: Boolean

    @Serializable
    data class EntryPoint(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        val taint: List<SerializedTaintAssignAction>
    ) : SerializedRule

    @Serializable
    data class Source(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        val condition: SerializedCondition? = null,
        val taint: List<SerializedTaintAssignAction>
    ) : SerializedRule

    @Serializable
    data class Cleaner(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        val condition: SerializedCondition? = null,
        val cleans: List<SerializedTaintCleanAction>
    ) : SerializedRule

    @Serializable
    data class PassThrough(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        val condition: SerializedCondition? = null,
        val copy: List<SerializedTaintPassAction>
    ) : SerializedRule

    @Serializable
    data class Sink(
        override val function: SerializedFunctionNameMatcher,
        override val signature: SerializedSignatureMatcher? = null,
        override val overrides: Boolean = true,
        val condition: SerializedCondition? = null,
        val cwe: List<Int>,
        val note: String
    ) : SerializedRule
}
