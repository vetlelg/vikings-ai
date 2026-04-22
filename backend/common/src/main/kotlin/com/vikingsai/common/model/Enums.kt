package com.vikingsai.common.model

import kotlinx.serialization.Serializable

@Serializable
enum class TerrainType { GRASS, FOREST, WATER, MOUNTAIN, BEACH, VILLAGE }

@Serializable
enum class ResourceType { TIMBER, FISH, IRON, FURS }

@Serializable
enum class AgentRole { JARL, WARRIOR, FISHERMAN, SHIPBUILDER }

@Serializable
enum class ActionType { MOVE, GATHER, FIGHT, BUILD, FLEE, SPEAK, IDLE }

@Serializable
enum class TaskType { GATHER, DEPOSIT, FIGHT, FLEE, MOVE_TO, IDLE }

@Serializable
enum class AgentStatus { ALIVE, DEAD, THINKING }

@Serializable
enum class TimeOfDay { DAWN, DAY, DUSK, NIGHT }

@Serializable
enum class Weather { CLEAR, SNOW, STORM }

@Serializable
enum class GameStatus { IN_PROGRESS, VICTORY }

@Serializable
enum class EventType {
    DRAGON_SIGHTED, DRAGON_DEFEATED, NIGHT_FALLING, DAWN_BREAKING,
    RAID_INCOMING, WOLF_SPOTTED, AGENT_DIED, BUILDING_COMPLETE,
    RESOURCE_GATHERED, COMBAT, WEATHER_CHANGE, AGENT_SPOKE,
    LONGSHIP_COMPLETE
}

@Serializable
enum class EntityType { WOLF, DRAGON, RESOURCE_NODE }

@Serializable
enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
