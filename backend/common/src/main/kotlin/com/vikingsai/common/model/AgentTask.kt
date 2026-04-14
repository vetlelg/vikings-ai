package com.vikingsai.common.model

import kotlinx.serialization.Serializable

/**
 * A high-level task assigned to an agent. The engine executes this
 * mechanically over multiple ticks until completion or interruption.
 */
@Serializable
data class AgentTask(
    val tick: Int,
    val agentName: String,
    val taskType: TaskType,
    val targetResourceType: ResourceType? = null,
    val targetPosition: Position? = null,
    val reasoning: String = ""
)
