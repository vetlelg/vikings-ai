package com.vikingsai.common.model

import kotlinx.serialization.Serializable

/**
 * A strategic directive issued by the Jarl (colony leader).
 *
 * The Jarl periodically evaluates the colony's overall situation and
 * publishes directives to the agent-directives topic. Other agents
 * read these as context for their own LLM-based decision-making.
 *
 * Agents may follow or override directives based on local conditions —
 * this models the real tension between central coordination and local autonomy.
 */
@Serializable
data class JarlDirective(
    val tick: Int,
    val assessment: String,
    val assignments: List<AgentAssignment> = emptyList()
)

@Serializable
data class AgentAssignment(
    val agentName: String,
    val directive: String
)
