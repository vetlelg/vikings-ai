package com.vikingsai.agent

import com.vikingsai.common.kafka.GameJson
import com.vikingsai.common.kafka.Topics
import com.vikingsai.common.kafka.createConsumer
import com.vikingsai.common.kafka.createProducer
import com.vikingsai.common.llm.LlmProvider
import com.vikingsai.common.model.rts.*
import kotlinx.coroutines.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

class AgentManager(
    private val bootstrapServers: String,
    private val provider: LlmProvider,
    private val maxTokens: Int,
) {
    private val log = LoggerFactory.getLogger(AgentManager::class.java)
    private val rateLimiter = Semaphore(2)

    private val commanders = ConcurrentHashMap<String, CommanderAgent>()
    private val unitAgents = ConcurrentHashMap<Int, UnitAgent>()
    private val agentJobs = ConcurrentHashMap<String, Job>()

    private lateinit var producer: KafkaProducer<String, String>
    private var latestState: GameStateSnapshot? = null

    suspend fun run() = coroutineScope {
        producer = withContext(Dispatchers.IO) { createProducer(bootstrapServers) }

        // Consume game state and events
        val consumer = withContext(Dispatchers.IO) {
            createConsumer(
                bootstrapServers = bootstrapServers,
                groupId = "agent-manager-${System.currentTimeMillis()}",
                topics = listOf(Topics.GAME_STATE, Topics.GAME_EVENTS),
                fromBeginning = false,
            )
        }

        log.info("Agent manager started, consuming game-state and game-events")

        try {
            while (isActive) {
                val records = withContext(Dispatchers.IO) { consumer.poll(Duration.ofMillis(200)) }

                for (record in records) {
                    when (record.topic()) {
                        Topics.GAME_STATE -> {
                            try {
                                val state = GameJson.decodeFromString<GameStateSnapshot>(record.value())
                                latestState = state
                                updateAgents(state, this)
                            } catch (e: Exception) {
                                log.warn("Failed to parse game state: ${e.message}")
                            }
                        }
                        Topics.GAME_EVENTS -> {
                            try {
                                val event = GameJson.decodeFromString<GameEvent>(record.value())
                                handleEvent(event)
                            } catch (e: Exception) {
                                log.warn("Failed to parse game event: ${e.message}")
                            }
                        }
                    }
                }
            }
        } finally {
            withContext(Dispatchers.IO) {
                consumer.close()
                producer.close()
            }
            log.info("Agent manager stopped")
        }
    }

    private fun updateAgents(state: GameStateSnapshot, scope: CoroutineScope) {
        for (faction in state.factions) {
            // Ensure commander exists for each faction
            if (!commanders.containsKey(faction.faction)) {
                val commander = CommanderAgent(
                    faction = faction.faction,
                    provider = provider,
                    producer = producer,
                    bootstrapServers = bootstrapServers,
                    maxTokens = maxTokens,
                    rateLimiter = rateLimiter,
                )
                commanders[faction.faction] = commander
                val job = scope.launch { commander.run() }
                agentJobs["commander-${faction.faction}"] = job
                log.info("Spawned commander for ${faction.faction}")
            }

            // Update commander with latest state
            commanders[faction.faction]?.updateState(state)

            // Ensure unit agent exists for each living unit
            for (unit in faction.units) {
                if (!unitAgents.containsKey(unit.unitId)) {
                    val agent = UnitAgent(
                        unitId = unit.unitId,
                        unitType = unit.unitType,
                        faction = faction.faction,
                        producer = producer,
                        bootstrapServers = bootstrapServers,
                    )
                    unitAgents[unit.unitId] = agent
                    val job = scope.launch { agent.run() }
                    agentJobs["unit-${unit.unitId}"] = job
                    log.info("Spawned agent for ${faction.faction} ${unit.unitType} #${unit.unitId}")
                }

                // Update unit agent with latest state
                unitAgents[unit.unitId]?.updateState(state)
            }
        }
    }

    private fun handleEvent(event: GameEvent) {
        when (event.eventType) {
            "unit_killed" -> {
                val unitId = event.unitId ?: return
                val agent = unitAgents.remove(unitId)
                if (agent != null) {
                    agent.stop()
                    agentJobs.remove("unit-$unitId")?.cancel()
                    log.info("Removed agent for ${event.faction} unit #$unitId (killed)")
                }
            }
            "unit_trained" -> {
                // Agent will be spawned on next state update when the unit appears
                log.info("Unit trained: ${event.description}")
            }
        }

        // Forward events to relevant commanders
        for ((faction, commander) in commanders) {
            if (event.faction == null || event.faction == faction) {
                commander.handleEvent(event)
            }
        }

        // Forward events to relevant unit agents
        for ((_, agent) in unitAgents) {
            if (event.faction == null || event.faction == agent.faction) {
                agent.handleEvent(event)
            }
        }
    }
}
