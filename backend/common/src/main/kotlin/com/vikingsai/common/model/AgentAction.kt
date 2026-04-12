package com.vikingsai.common.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentAction(
    val tick: Int,
    val agentName: String,
    val action: ActionType,
    val direction: String? = null,
    val targetPosition: Position? = null,
    val reasoning: String = ""
)
