package com.vikingsai.common.model

import kotlinx.serialization.Serializable

@Serializable
data class WorldEvent(
    val tick: Int,
    val eventType: EventType,
    val description: String,
    val severity: Severity,
    val affectedPositions: List<Position> = emptyList()
)
