package com.vikingsai.common.model.rts

import kotlinx.serialization.Serializable

@Serializable
data class GameStateSnapshot(
    val tick: Int,
    val factions: List<FactionSnapshot>,
    val resourceNodes: List<ResourceNodeSnapshot>,
)

@Serializable
data class FactionSnapshot(
    val faction: String,
    val resources: ResourceCounts,
    val population: Int,
    val units: List<UnitSnapshot>,
    val buildings: List<BuildingSnapshot>,
)

@Serializable
data class ResourceCounts(
    val wood: Int = 0,
    val stone: Int = 0,
    val gold: Int = 0,
    val food: Int = 0,
)

@Serializable
data class UnitSnapshot(
    val unitId: Int,
    val unitType: String,
    val tileX: Int,
    val tileY: Int,
    val hp: Int,
    val maxHp: Int,
    val state: String,
    val carryType: String? = null,
    val carryAmount: Int = 0,
)

@Serializable
data class BuildingSnapshot(
    val buildingId: Int,
    val buildingType: String,
    val tileX: Int,
    val tileY: Int,
    val hp: Int,
    val maxHp: Int,
    val built: Boolean,
    val constructionProgress: Double = 1.0,
    val trainingQueue: List<TrainingQueueEntry> = emptyList(),
)

@Serializable
data class TrainingQueueEntry(
    val unitType: String,
    val progress: Double,
)

@Serializable
data class ResourceNodeSnapshot(
    val nodeId: Int,
    val resourceType: String,
    val tileX: Int,
    val tileY: Int,
    val remaining: Int,
)

@Serializable
data class GameEvent(
    val tick: Int,
    val eventType: String,
    val faction: String? = null,
    val unitId: Int? = null,
    val buildingId: Int? = null,
    val targetId: Int? = null,
    val position: IntArray? = null,
    val description: String = "",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameEvent) return false
        return tick == other.tick && eventType == other.eventType && description == other.description
    }

    override fun hashCode(): Int {
        var result = tick
        result = 31 * result + eventType.hashCode()
        result = 31 * result + description.hashCode()
        return result
    }
}

@Serializable
data class CommanderOrder(
    val tick: Int,
    val faction: String,
    val strategy: String,
    val assignments: List<UnitAssignment> = emptyList(),
    val buildOrders: List<BuildOrder> = emptyList(),
    val trainOrders: List<TrainOrder> = emptyList(),
)

@Serializable
data class UnitAssignment(
    val unitId: Int,
    val task: String,
    val targetX: Int? = null,
    val targetY: Int? = null,
    val targetId: Int? = null,
    val reasoning: String = "",
)

@Serializable
data class BuildOrder(
    val buildingType: String,
    val tileX: Int,
    val tileY: Int,
)

@Serializable
data class TrainOrder(
    val buildingId: Int,
    val unitType: String,
)

@Serializable
data class UnitDecision(
    val tick: Int,
    val faction: String,
    val unitId: Int,
    val action: String,
    val targetX: Int? = null,
    val targetY: Int? = null,
    val targetId: Int? = null,
    val reasoning: String = "",
)

@Serializable
data class UnitObservation(
    val tick: Int,
    val faction: String,
    val unitId: Int,
    val observationType: String,
    val description: String,
    val tileX: Int,
    val tileY: Int,
)
