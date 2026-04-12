package com.vikingsai.common.model

import kotlinx.serialization.Serializable

@Serializable
data class SagaLogEntry(
    val tick: Int,
    val text: String
)
