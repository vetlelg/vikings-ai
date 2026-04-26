package com.vikingsai.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BridgeEnvelope(
    val topic: String,
    val timestamp: Long,
    val payload: JsonElement
)
