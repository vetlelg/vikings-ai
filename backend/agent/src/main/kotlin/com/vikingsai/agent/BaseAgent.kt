package com.vikingsai.agent

import com.vikingsai.agent.prompt.PromptBuilder
import com.vikingsai.common.kafka.GameJson
import com.vikingsai.common.kafka.Topics
import com.vikingsai.common.kafka.createConsumer
import com.vikingsai.common.llm.LlmProvider
import com.vikingsai.common.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Semaphore

/**
 * Base class for all Viking agents. Each agent:
 * 1. Consumes world-state and world-events from Kafka
 * 2. Decides when a new task is needed (no task, task completed, interrupt)
 * 3. Calls the LLM provider for a high-level task decision
 * 4. Publishes the task to agent-tasks (engine executes it mechanically)
 *
 * Agents only call the LLM when replanning — not every tick.
 */
open class BaseAgent(
    val name: String,
    val role: AgentRole,
    val personality: String,
    protected val provider: LlmProvider,
    private val producer: KafkaProducer<String, String>,
    protected val bootstrapServers: String,
    protected val maxTokens: Int = 150,
    protected val rateLimiter: Semaphore? = null
) {
    private val log = LoggerFactory.getLogger("Agent-$name")

    private var latestWorldState: WorldState? = null
    private val recentEvents = mutableListOf<WorldEvent>()
    private var lastProcessedTick = -1
    private var lastTaskTick = -1

    open suspend fun run() {
        log.info("$name the ${role.name} awakens")

        val consumer = withContext(Dispatchers.IO) {
            createConsumer(
                bootstrapServers = bootstrapServers,
                groupId = "agent-$name",
                topics = listOf(Topics.WORLD_STATE, Topics.WORLD_EVENTS),
                fromBeginning = false
            )
        }

        try {
            while (kotlin.coroutines.coroutineContext.isActive) {
                val records = withContext(Dispatchers.IO) {
                    consumer.poll(Duration.ofMillis(1000))
                }

                for (record in records) {
                    when (record.topic()) {
                        Topics.WORLD_STATE -> {
                            try {
                                latestWorldState = GameJson.decodeFromString<WorldState>(record.value())
                            } catch (e: Exception) {
                                log.warn("Failed to parse world state: ${e.message}")
                            }
                        }
                        Topics.WORLD_EVENTS -> {
                            try {
                                val event = GameJson.decodeFromString<WorldEvent>(record.value())
                                recentEvents.add(event)
                                if (recentEvents.size > 20) recentEvents.removeFirst()
                            } catch (e: Exception) {
                                log.warn("Failed to parse world event: ${e.message}")
                            }
                        }
                    }
                }

                val ws = latestWorldState ?: continue
                if (ws.tick <= lastProcessedTick) continue
                lastProcessedTick = ws.tick

                val self = ws.agents.find { it.name == name }
                if (self != null && self.status == AgentStatus.DEAD) continue

                // Only replan when needed
                if (self != null && needsNewTask(self, ws)) {
                    try {
                        sendTask(ws)
                    } catch (e: Exception) {
                        log.error("Error planning task at tick ${ws.tick}: ${e.message}")
                    }
                }
            }
        } finally {
            withContext(Dispatchers.IO) { consumer.close() }
            log.info("$name goes to sleep")
        }
    }

    /**
     * Determines if the agent needs to replan.
     */
    private fun needsNewTask(self: AgentSnapshot, ws: WorldState): Boolean {
        // No current task — definitely need one
        if (self.currentTaskType == null) return true

        // Interrupt: nearby threat and not already fighting/fleeing
        if (self.currentTaskType != TaskType.FIGHT && self.currentTaskType != TaskType.FLEE) {
            val nearestThreat = ws.threats.minByOrNull {
                Math.abs(it.position.x - self.position.x) + Math.abs(it.position.y - self.position.y)
            }
            if (nearestThreat != null) {
                val dist = Math.abs(nearestThreat.position.x - self.position.x) +
                        Math.abs(nearestThreat.position.y - self.position.y)
                if (dist <= 4) return true
            }
        }

        // Interrupt: health critical and not already fleeing/idle
        if (self.health < 30 && self.currentTaskType != TaskType.FLEE && self.currentTaskType != TaskType.IDLE) {
            return true
        }

        return false
    }

    protected open suspend fun sendTask(worldState: WorldState) {
        val systemPrompt = PromptBuilder.buildSystemPrompt(name, role, personality)
        val userPrompt = PromptBuilder.buildUserPrompt(name, worldState, recentEvents)

        rateLimiter?.let { withContext(Dispatchers.IO) { it.acquire() } }
        try {
            val response = provider.complete(systemPrompt, userPrompt, maxTokens)
            val task = parseTask(response, worldState.tick)

            if (task != null) {
                val json = GameJson.encodeToString(AgentTask.serializer(), task)
                withContext(Dispatchers.IO) {
                    producer.send(ProducerRecord(Topics.AGENT_TASKS, name, json))
                }
                lastTaskTick = worldState.tick
                log.info("[Tick ${worldState.tick}] TASK: ${task.taskType}${if (task.targetResourceType != null) " (${task.targetResourceType})" else ""} — ${task.reasoning.take(60)}")
            }
        } finally {
            rateLimiter?.release()
        }
    }

    private fun parseTask(response: String, tick: Int): AgentTask? {
        return try {
            val jsonStr = extractJson(response)
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            val taskTypeStr = json["taskType"]?.jsonPrimitive?.content?.uppercase() ?: return null
            val taskType = TaskType.valueOf(taskTypeStr)

            val targetResource = json["targetResourceType"]?.jsonPrimitive?.content?.uppercase()?.let {
                try { ResourceType.valueOf(it) } catch (_: Exception) { null }
            }

            val targetPos = json["targetPosition"]?.jsonObject?.let { pos ->
                val x = pos["x"]?.jsonPrimitive?.content?.toIntOrNull()
                val y = pos["y"]?.jsonPrimitive?.content?.toIntOrNull()
                if (x != null && y != null) Position(x, y) else null
            }

            AgentTask(
                tick = tick,
                agentName = name,
                taskType = taskType,
                targetResourceType = targetResource,
                targetPosition = targetPos,
                reasoning = json["reasoning"]?.jsonPrimitive?.content ?: ""
            )
        } catch (e: Exception) {
            log.warn("Failed to parse LLM task response: ${e.message}. Raw: ${response.take(100)}")
            AgentTask(
                tick = tick,
                agentName = name,
                taskType = TaskType.IDLE,
                reasoning = "Failed to decide"
            )
        }
    }

    private fun extractJson(text: String): String {
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?(\\{.*?})\\s*\\n?```", RegexOption.DOT_MATCHES_ALL)
        codeBlockPattern.find(text)?.let { return it.groupValues[1].trim() }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) return text.substring(start, end + 1)
        return text.trim()
    }
}
