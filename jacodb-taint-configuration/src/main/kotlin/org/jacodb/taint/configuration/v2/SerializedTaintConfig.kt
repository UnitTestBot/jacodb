package org.jacodb.taint.configuration.v2

import kotlinx.serialization.Serializable

@Serializable
data class SerializedTaintConfig(
    val entryPoint: List<SerializedRule.EntryPoint>,
    val source: List<SerializedRule.Source>,
    val sink: List<SerializedRule.Sink>,
    val passThrough: List<SerializedRule.PassThrough>,
    val cleaner: List<SerializedRule.Cleaner>
)
