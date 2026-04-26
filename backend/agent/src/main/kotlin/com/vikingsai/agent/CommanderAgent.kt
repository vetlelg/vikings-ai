package com.vikingsai.agent

import com.vikingsai.common.kafka.GameJson
import com.vikingsai.common.kafka.Topics
import com.vikingsai.common.llm.LlmProvider
import com.vikingsai.common.model.rts.*
import kotlinx.coroutines.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.concurrent.Semaphore

class CommanderAgent(
    val faction: String,
    private val provider: LlmProvider,
    private val producer: KafkaProducer<String, String>,
    private val bootstrapServers: String,
    private val maxTokens: Int,
    private val rateLimiter: Semaphore,
) {
    private val log = LoggerFactory.getLogger("Commander-$faction")

    @Volatile private var latestState: GameStateSnapshot? = null
    @Volatile private var lastOrderTick = -1
    private val recentEvents = mutableListOf<GameEvent>()
    private var barracksOrdered = false

    private val orderIntervalTicks = 50 // ~5 seconds at 10 ticks/sec

    fun updateState(state: GameStateSnapshot) {
        latestState = state
    }

    fun handleEvent(event: GameEvent) {
        synchronized(recentEvents) {
            recentEvents.add(event)
            if (recentEvents.size > 30) recentEvents.removeFirst()
        }
    }

    suspend fun run() {
        log.info("Commander for $faction started")

        try {
            while (currentCoroutineContext().isActive) {
                delay(500)

                val state = latestState ?: continue
                if (state.tick - lastOrderTick < orderIntervalTicks) continue

                val order = decide(state)
                if (order != null) {
                    publish(order)
                    lastOrderTick = state.tick
                }
            }
        } catch (e: CancellationException) {
            // normal shutdown
        }

        log.info("Commander for $faction stopped")
    }

    private fun decide(state: GameStateSnapshot): CommanderOrder? {
        val myFaction = state.factions.find { it.faction == faction } ?: return null
        val enemyFaction = state.factions.find { it.faction != faction }

        val workers = myFaction.units.filter { it.unitType == "worker" }
        val warriors = myFaction.units.filter { it.unitType == "warrior" }
        val idleWorkers = workers.filter { it.state == "idle" }
        val idleWarriors = warriors.filter { it.state == "idle" }
        val tc = myFaction.buildings.find { it.buildingType == "townCenter" }
        val barracks = myFaction.buildings.find { it.buildingType == "barracks" }
        val hasBarracks = barracks?.built == true

        val assignments = mutableListOf<UnitAssignment>()
        val buildOrders = mutableListOf<BuildOrder>()
        val trainOrders = mutableListOf<TrainOrder>()
        var strategy = "developing economy"

        // Train workers if we have few
        val maxWorkers = if (hasBarracks) 4 else 5
        if (tc != null && workers.size < maxWorkers && myFaction.population < 10) {
            if (tc.trainingQueue.isEmpty() && myFaction.resources.food >= 50) {
                trainOrders.add(TrainOrder(buildingId = tc.buildingId, unitType = "worker"))
            }
        }

        // Train warriors when we have barracks
        if (hasBarracks && myFaction.population < 10) {
            if (barracks.trainingQueue.isEmpty()
                && myFaction.resources.food >= 60
                && myFaction.resources.gold >= 30
            ) {
                trainOrders.add(TrainOrder(buildingId = barracks.buildingId, unitType = "warrior"))
            }
        }

        // Build barracks when affordable
        if (!barracksOrdered && tc != null
            && myFaction.resources.wood >= 100
            && myFaction.resources.stone >= 50
            && barracks == null
        ) {
            val bx = if (faction == "blue") tc.tileX + 4 else tc.tileX - 4
            buildOrders.add(BuildOrder(buildingType = "barracks", tileX = bx, tileY = tc.tileY))
            barracksOrdered = true
            strategy = "building barracks"
        }

        // Assign idle workers to gather or build
        val unfinishedBarracks = myFaction.buildings.find {
            it.buildingType == "barracks" && !it.built
        }

        for (worker in idleWorkers) {
            // Send one worker to build if barracks is under construction
            if (unfinishedBarracks != null) {
                val buildersAssigned = assignments.count { it.task == "build" }
                if (buildersAssigned < 1) {
                    assignments.add(UnitAssignment(
                        unitId = worker.unitId,
                        task = "build",
                        targetId = unfinishedBarracks.buildingId,
                        reasoning = "building barracks",
                    ))
                    continue
                }
            }

            // Gather what's needed
            val resourceType = pickNeededResource(myFaction.resources, hasBarracks)
            val node = findNearestNode(worker, state.resourceNodes, resourceType)
            if (node != null) {
                assignments.add(UnitAssignment(
                    unitId = worker.unitId,
                    task = "gather",
                    targetId = node.nodeId,
                    reasoning = "gathering $resourceType",
                ))
            }
        }

        // Send idle warriors toward enemy
        if (idleWarriors.isNotEmpty() && enemyFaction != null) {
            strategy = if (warriors.size >= 3) "attacking" else "building army"

            if (warriors.size >= 2) {
                val target = findAttackTarget(enemyFaction)
                if (target != null) {
                    for (warrior in idleWarriors) {
                        assignments.add(UnitAssignment(
                            unitId = warrior.unitId,
                            task = "attack",
                            targetId = target.first,
                            targetX = target.second,
                            targetY = target.third,
                            reasoning = "attacking enemy",
                        ))
                    }
                }
            }
        }

        if (assignments.isEmpty() && buildOrders.isEmpty() && trainOrders.isEmpty()) {
            return null
        }

        return CommanderOrder(
            tick = state.tick,
            faction = faction,
            strategy = strategy,
            assignments = assignments,
            buildOrders = buildOrders,
            trainOrders = trainOrders,
        )
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

    private fun findNearestNode(
        unit: UnitSnapshot,
        nodes: List<ResourceNodeSnapshot>,
        type: String,
    ): ResourceNodeSnapshot? {
        val matching = nodes.filter { it.resourceType == type && it.remaining > 0 }
        if (matching.isNotEmpty()) {
            return matching.minByOrNull { maxOf(Math.abs(it.tileX - unit.tileX), Math.abs(it.tileY - unit.tileY)) }
        }
        return nodes.filter { it.remaining > 0 }
            .minByOrNull { maxOf(Math.abs(it.tileX - unit.tileX), Math.abs(it.tileY - unit.tileY)) }
    }

    private fun findAttackTarget(enemy: FactionSnapshot): Triple<Int, Int, Int>? {
        // Prefer units over buildings
        val target = enemy.units.firstOrNull()
        if (target != null) return Triple(target.unitId, target.tileX, target.tileY)

        val building = enemy.buildings.firstOrNull()
        if (building != null) return Triple(building.buildingId, building.tileX, building.tileY)

        return null
    }

    private suspend fun publish(order: CommanderOrder) {
        val topic = Topics.commanderOrders(faction)
        val json = GameJson.encodeToString(CommanderOrder.serializer(), order)
        withContext(Dispatchers.IO) {
            producer.send(ProducerRecord(topic, faction, json))
        }
        log.info("[Tick ${order.tick}] Strategy: ${order.strategy} | ${order.assignments.size} assignments, ${order.buildOrders.size} builds, ${order.trainOrders.size} trains")
    }
}
