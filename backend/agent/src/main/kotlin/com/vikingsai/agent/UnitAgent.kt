package com.vikingsai.agent

import com.vikingsai.common.kafka.GameJson
import com.vikingsai.common.kafka.Topics
import com.vikingsai.common.model.rts.*
import kotlinx.coroutines.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

/**
 * A persistent agent for a single unit. Runs a behavior tree to decide actions.
 * Currently stub/rule-based. LLM reasoning will be added for judgment calls.
 */
class UnitAgent(
    val unitId: Int,
    val unitType: String,
    val faction: String,
    private val producer: KafkaProducer<String, String>,
    private val bootstrapServers: String,
) {
    private val log = LoggerFactory.getLogger("Unit-$faction-$unitType-$unitId")

    @Volatile private var latestState: GameStateSnapshot? = null
    @Volatile private var stopped = false
    private var lastDecisionTick = -1
    private var currentAction: String? = null
    private val recentEvents = mutableListOf<GameEvent>()

    // Memory
    private val knownResourceLocations = mutableListOf<ResourceNodeSnapshot>()

    private val decisionCooldownTicks = 10 // Don't re-decide faster than once per second

    fun updateState(state: GameStateSnapshot) {
        latestState = state
    }

    fun handleEvent(event: GameEvent) {
        synchronized(recentEvents) {
            recentEvents.add(event)
            if (recentEvents.size > 20) recentEvents.removeFirst()
        }
    }

    fun stop() {
        stopped = true
    }

    suspend fun run() {
        log.info("$faction $unitType #$unitId agent started")

        try {
            while (currentCoroutineContext().isActive && !stopped) {
                delay(200)

                val state = latestState ?: continue
                val myFaction = state.factions.find { it.faction == faction } ?: continue
                val me = myFaction.units.find { it.unitId == unitId } ?: continue

                if (state.tick - lastDecisionTick < decisionCooldownTicks) continue

                // Update memory
                updateMemory(state)

                // Run behavior tree
                if (needsDecision(me)) {
                    val decision = behaviorTree(me, myFaction, state)
                    if (decision != null) {
                        publish(decision)
                        lastDecisionTick = state.tick
                        currentAction = decision.action
                    }
                }
            }
        } catch (e: CancellationException) {
            // normal shutdown
        }

        log.info("$faction $unitType #$unitId agent stopped")
    }

    private fun needsDecision(me: UnitSnapshot): Boolean {
        return me.state == "idle"
    }

    private fun updateMemory(state: GameStateSnapshot) {
        // Remember resource locations we can see
        val me = state.factions.find { it.faction == faction }
            ?.units?.find { it.unitId == unitId } ?: return

        for (node in state.resourceNodes) {
            val dist = maxOf(Math.abs(node.tileX - me.tileX), Math.abs(node.tileY - me.tileY))
            if (dist <= 10) {
                val existing = knownResourceLocations.indexOfFirst { it.nodeId == node.nodeId }
                if (existing >= 0) {
                    knownResourceLocations[existing] = node
                } else {
                    knownResourceLocations.add(node)
                }
            }
        }

        // Remove depleted nodes from memory
        knownResourceLocations.removeAll { it.remaining <= 0 }
    }

    private fun behaviorTree(
        me: UnitSnapshot,
        myFaction: FactionSnapshot,
        state: GameStateSnapshot,
    ): UnitDecision? {
        return when (unitType) {
            "worker" -> workerBehavior(me, myFaction, state)
            "warrior" -> warriorBehavior(me, myFaction, state)
            else -> null
        }
    }

    private fun workerBehavior(
        me: UnitSnapshot,
        myFaction: FactionSnapshot,
        state: GameStateSnapshot,
    ): UnitDecision? {
        // If carrying resources, return to TC
        if (me.carryAmount > 0) {
            val tc = myFaction.buildings.find { it.buildingType == "townCenter" }
            if (tc != null) {
                return makeDecision(state.tick, "move",
                    targetX = tc.tileX + 1, targetY = tc.tileY + 1,
                    reasoning = "returning ${me.carryType} to town center",
                )
            }
        }

        // If there's an unfinished building, help build it
        val unfinished = myFaction.buildings.find { !it.built }
        if (unfinished != null) {
            // Only send if not many workers already building
            return makeDecision(state.tick, "build",
                targetId = unfinished.buildingId,
                reasoning = "building ${unfinished.buildingType}",
            )
        }

        // Gather resources
        val hasBarracks = myFaction.buildings.any { it.buildingType == "barracks" && it.built }
        val neededType = pickNeededResource(myFaction.resources, hasBarracks)
        val node = findNearestResource(me, state.resourceNodes, neededType)
        if (node != null) {
            return makeDecision(state.tick, "gather",
                targetId = node.nodeId,
                reasoning = "gathering $neededType",
            )
        }

        // Gather anything if preferred type unavailable
        val anyNode = state.resourceNodes.filter { it.remaining > 0 }
            .minByOrNull { maxOf(Math.abs(it.tileX - me.tileX), Math.abs(it.tileY - me.tileY)) }
        if (anyNode != null) {
            return makeDecision(state.tick, "gather",
                targetId = anyNode.nodeId,
                reasoning = "gathering available resources",
            )
        }

        return null
    }

    private fun warriorBehavior(
        me: UnitSnapshot,
        myFaction: FactionSnapshot,
        state: GameStateSnapshot,
    ): UnitDecision? {
        val enemyFaction = state.factions.find { it.faction != faction } ?: return null

        // Attack nearest enemy unit
        val nearestEnemy = enemyFaction.units
            .minByOrNull { maxOf(Math.abs(it.tileX - me.tileX), Math.abs(it.tileY - me.tileY)) }
        if (nearestEnemy != null) {
            return makeDecision(state.tick, "attack",
                targetId = nearestEnemy.unitId,
                reasoning = "attacking enemy ${nearestEnemy.unitType}",
            )
        }

        // Attack enemy buildings
        val nearestBuilding = enemyFaction.buildings
            .minByOrNull { maxOf(Math.abs(it.tileX - me.tileX), Math.abs(it.tileY - me.tileY)) }
        if (nearestBuilding != null) {
            return makeDecision(state.tick, "attack",
                targetId = nearestBuilding.buildingId,
                reasoning = "attacking enemy ${nearestBuilding.buildingType}",
            )
        }

        return null
    }

    private fun pickNeededResource(resources: ResourceCounts, hasBarracks: Boolean): String {
        if (!hasBarracks) {
            if (resources.wood < 100) return "wood"
            if (resources.stone < 50) return "stone"
        }
        if (resources.food < 50) return "food"
        if (resources.gold < 30) return "gold"
        if (resources.wood < 50) return "wood"
        return "food"
    }

    private fun findNearestResource(
        me: UnitSnapshot,
        nodes: List<ResourceNodeSnapshot>,
        type: String,
    ): ResourceNodeSnapshot? {
        return nodes.filter { it.resourceType == type && it.remaining > 0 }
            .minByOrNull { maxOf(Math.abs(it.tileX - me.tileX), Math.abs(it.tileY - me.tileY)) }
    }

    private fun makeDecision(
        tick: Int,
        action: String,
        targetX: Int? = null,
        targetY: Int? = null,
        targetId: Int? = null,
        reasoning: String = "",
    ): UnitDecision {
        return UnitDecision(
            tick = tick,
            faction = faction,
            unitId = unitId,
            action = action,
            targetX = targetX,
            targetY = targetY,
            targetId = targetId,
            reasoning = reasoning,
        )
    }

    private suspend fun publish(decision: UnitDecision) {
        val topic = Topics.unitDecisions(faction)
        val json = GameJson.encodeToString(UnitDecision.serializer(), decision)
        withContext(Dispatchers.IO) {
            producer.send(ProducerRecord(topic, "unit-$unitId", json))
        }
        log.debug("[Tick ${decision.tick}] Unit #$unitId: ${decision.action} — ${decision.reasoning}")
    }
}
