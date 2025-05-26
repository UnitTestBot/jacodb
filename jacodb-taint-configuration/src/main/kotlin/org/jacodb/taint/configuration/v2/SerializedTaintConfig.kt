package org.jacodb.taint.configuration.v2

import kotlinx.serialization.Serializable

@Serializable
data class SerializedTaintConfig(
    val entryPoint: List<SerializedRule.EntryPoint>? = null,
    val source: List<SerializedRule.Source>? = null,
    val sink: List<SerializedRule.Sink>? = null,
    val passThrough: List<SerializedRule.PassThrough>? = null,
    val cleaner: List<SerializedRule.Cleaner>? = null,
    val methodExitSink: List<SerializedRule.MethodExitSink>? = null,
)
