package com.vikingsai.common.model

import kotlinx.serialization.Serializable

@Serializable
data class WorldState(
    val tick: Int,
    val grid: List<List<TerrainType>>,
    val agents: List<AgentSnapshot>,
    val entities: List<EntitySnapshot>,
    val colonyResources: ColonyResources,
    val timeOfDay: TimeOfDay,
    val weather: Weather,
    val threats: List<ThreatSnapshot>
)

@Serializable
data class AgentSnapshot(
    val name: String,
    val role: AgentRole,
    val position: Position,
    val health: Int,
    val inventory: Map<ResourceType, Int> = emptyMap(),
    val status: AgentStatus = AgentStatus.ALIVE
)

@Serializable
data class EntitySnapshot(
    val id: String,
    val type: EntityType,
    val position: Position,
    val subtype: String? = null
)

@Serializable
data class ColonyResources(
    val timber: Int = 0,
    val fish: Int = 0,
    val iron: Int = 0,
    val furs: Int = 0
)

@Serializable
data class ThreatSnapshot(
    val id: String,
    val type: String,
    val position: Position,
    val severity: Severity
)
