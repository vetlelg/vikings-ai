package com.vikingsai.common.model

import kotlinx.serialization.Serializable

/**
 * An observation broadcast by an agent about what it sees in its vicinity.
 *
 * Agents have partial visibility (nearby tiles only). By publishing observations
 * to a shared Kafka topic, the colony builds collective situational awareness
 * from distributed local knowledge — the same pattern used in sensor networks,
 * IoT systems, and distributed monitoring.
 */
@Serializable
data class AgentObservation(
    val tick: Int,
    val agentName: String,
    val type: ObservationType,
    val description: String,
    val position: Position
)

@Serializable
enum class ObservationType {
    RESOURCE_FOUND,
    RESOURCE_DEPLETED,
    THREAT_SPOTTED,
    AREA_CLEAR
}
