package org.utbot.jcdb.impl.features

import kotlinx.serialization.Serializable

@Serializable
data class UsageFeatureRequest(
    val methodName: String?,
    val methodDesc: String?,
    val field: String?,
    val opcodes: Collection<Int>,
    val className: String
) : java.io.Serializable