package com.vikingsai.engine

import com.vikingsai.common.kafka.GameJson
import com.vikingsai.common.kafka.Topics
import com.vikingsai.common.model.*
import com.vikingsai.engine.entities.Dragon
import com.vikingsai.engine.entities.ThreatManager
import com.vikingsai.engine.tick.ActionResolver
import com.vikingsai.engine.tick.EventGenerator
import com.vikingsai.engine.world.MapGenerator
import com.vikingsai.engine.world.MutableAgent
import com.vikingsai.engine.world.WorldManager
import kotlinx.coroutines.delay
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.Duration

class GameLoop(
    private val producer: KafkaProducer<String, String>,
    private val actionConsumer: KafkaConsumer<String, String>,
    private val tickRateMs: Long,
    gridWidth: Int = 20,
    gridHeight: Int = 20
) {
    private val log = LoggerFactory.getLogger(GameLoop::class.java)

    private val grid = MapGenerator.generate(gridWidth, gridHeight)
    private val world = WorldManager(grid)
    private val actionResolver = ActionResolver(world)
    private val eventGenerator = EventGenerator(world)
    private val dragon = Dragon(world)
    private val threatManager = ThreatManager(world, dragon)

    init {
        initializeAgents()
        world.spawnResourceNodes(8)
    }

    private fun initializeAgents() {
        val villagePositions = MapGenerator.walkablePositions(grid)
            .filter { grid[it.y][it.x] == TerrainType.VILLAGE }
            .ifEmpty { MapGenerator.walkablePositions(grid) }

        val agentDefs = listOf(
            "Bjorn" to AgentRole.JARL,
            "Astrid" to AgentRole.WARRIOR,
            "Erik" to AgentRole.FISHERMAN,
            "Ingrid" to AgentRole.SHIPBUILDER,
            "Sigurd" to AgentRole.SKALD
        )

        agentDefs.forEachIndexed { index, (name, role) ->
            val pos = villagePositions[index % villagePositions.size]
            world.agents.add(MutableAgent(name = name, role = role, position = pos))
        }

        log.info("Initialized ${world.agents.size} agents in the village")
    }

    suspend fun run() {
        log.info("Game loop starting. Tick rate: ${tickRateMs}ms")

        while (true) {
            world.advanceTick()
            val allEvents = mutableListOf<WorldEvent>()

            // 1. Consume agent actions (non-blocking)
            val actions = consumeActions()
            for (action in actions) {
                val events = actionResolver.resolve(action)
                allEvents.addAll(events)
            }

            // 2. Update threats (wolves, dragon, raids)
            allEvents.addAll(threatManager.update())

            // 3. Generate tick events (time, weather, resource spawning)
            allEvents.addAll(eventGenerator.generateTickEvents())

            // 4. Heal agents at village, respawn dead agents
            world.healAndRespawn()

            // 5. Publish world state
            publishWorldState()

            // 5. Publish world events
            for (event in allEvents) {
                publishWorldEvent(event)
            }

            if (world.tick % 10 == 0) {
                log.info("Tick ${world.tick} | ${world.timeOfDay} | ${world.weather} | " +
                    "Agents alive: ${world.agents.count { it.status == AgentStatus.ALIVE }} | " +
                    "Threats: ${world.entities.count { it.type == EntityType.WOLF || it.type == EntityType.DRAGON }}")
            }

            delay(tickRateMs)
        }
    }

    private fun consumeActions(): List<AgentAction> {
        val records = actionConsumer.poll(Duration.ZERO)
        return records.mapNotNull { record ->
            try {
                GameJson.decodeFromString<AgentAction>(record.value())
            } catch (e: Exception) {
                log.warn("Failed to parse agent action: ${e.message}")
                null
            }
        }
    }

    private fun publishWorldState() {
        val state = world.toWorldState()
        val json = GameJson.encodeToString(WorldState.serializer(), state)
        producer.send(ProducerRecord(Topics.WORLD_STATE, world.tick.toString(), json))
    }

    private fun publishWorldEvent(event: WorldEvent) {
        val json = GameJson.encodeToString(WorldEvent.serializer(), event)
        producer.send(ProducerRecord(Topics.WORLD_EVENTS, event.eventType.name, json))
        log.debug("Event: [${event.eventType}] ${event.description}")
    }
}
