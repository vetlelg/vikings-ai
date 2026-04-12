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
 * 2. Builds an LLM prompt with its personality + current world state
 * 3. Calls the LLM provider for a decision
 * 4. Publishes the action to agent-actions (or saga-log for Skald)
 *
 * Self-contained: receives all dependencies via constructor.
 * Can be extracted to a separate main() for independent process mode.
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

    suspend fun run() {
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
                // Poll for new messages
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

                // Act on the latest world state if it's a new tick
                val ws = latestWorldState ?: continue
                if (ws.tick <= lastProcessedTick) continue
                lastProcessedTick = ws.tick

                // Check if this agent is alive
                val self = ws.agents.find { it.name == name }
                if (self != null && self.status == AgentStatus.DEAD) {
                    log.debug("$name is dead, skipping tick ${ws.tick}")
                    continue
                }

                try {
                    processTickAction(ws)
                } catch (e: Exception) {
                    log.error("Error processing tick ${ws.tick}: ${e.message}")
                }
            }
        } finally {
            withContext(Dispatchers.IO) { consumer.close() }
            log.info("$name goes to sleep")
        }
    }

    protected open suspend fun processTickAction(worldState: WorldState) {
        val systemPrompt = PromptBuilder.buildSystemPrompt(name, role, personality)
        val userPrompt = PromptBuilder.buildUserPrompt(name, worldState, recentEvents)

        // Rate limit LLM calls
        rateLimiter?.let { withContext(Dispatchers.IO) { it.acquire() } }
        try {
            val response = provider.complete(systemPrompt, userPrompt, maxTokens)
            val action = parseAction(response, worldState.tick)

            if (action != null) {
                val json = GameJson.encodeToString(AgentAction.serializer(), action)
                withContext(Dispatchers.IO) {
                    producer.send(ProducerRecord(Topics.AGENT_ACTIONS, name, json))
                }
                log.info("[Tick ${worldState.tick}] ${action.action}${if (action.direction != null) " ${action.direction}" else ""} — ${action.reasoning.take(60)}")
            }
        } finally {
            rateLimiter?.release()
        }
    }

    private fun parseAction(response: String, tick: Int): AgentAction? {
        return try {
            // Try to extract JSON from response (LLM might wrap it in markdown)
            val jsonStr = extractJson(response)
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            val action = json["action"]?.jsonPrimitive?.content?.uppercase() ?: return null
            AgentAction(
                tick = tick,
                agentName = name,
                action = ActionType.valueOf(action),
                direction = json["direction"]?.jsonPrimitive?.content,
                reasoning = json["reasoning"]?.jsonPrimitive?.content ?: ""
            )
        } catch (e: Exception) {
            log.warn("Failed to parse LLM response: ${e.message}. Raw: ${response.take(100)}")
            // Fallback: idle
            AgentAction(
                tick = tick,
                agentName = name,
                action = ActionType.IDLE,
                reasoning = "Failed to decide"
            )
        }
    }

    private fun extractJson(text: String): String {
        // Handle markdown code blocks
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?(\\{.*?})\\s*\\n?```", RegexOption.DOT_MATCHES_ALL)
        codeBlockPattern.find(text)?.let { return it.groupValues[1].trim() }

        // Find first { ... } block
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1)
        }

        return text.trim()
    }
}
