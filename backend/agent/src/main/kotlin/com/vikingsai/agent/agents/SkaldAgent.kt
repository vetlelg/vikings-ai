package com.vikingsai.agent.agents

import com.vikingsai.agent.BaseAgent
import com.vikingsai.agent.prompt.PromptBuilder
import com.vikingsai.common.kafka.GameJson
import com.vikingsai.common.kafka.Topics
import com.vikingsai.common.kafka.createConsumer
import com.vikingsai.common.llm.LlmProvider
import com.vikingsai.common.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Semaphore

/**
 * The Skald does not gather resources or fight.
 * Instead, it observes the world and publishes dramatic narrations to the saga-log topic.
 * It also sends an IDLE task so the engine knows it's not doing anything active.
 */
class SkaldAgent(
    provider: LlmProvider,
    private val skaldProducer: KafkaProducer<String, String>,
    bootstrapServers: String,
    maxTokens: Int,
    rateLimiter: Semaphore?
) : BaseAgent(
    name = "Sigurd",
    role = AgentRole.SKALD,
    personality = """You are Sigurd, the Skald — poet, historian, and keeper of the saga.
You do NOT gather resources or fight. You OBSERVE and NARRATE.
Your role is to watch what happens in the settlement and compose dramatic narrations.
Write in the style of a Norse saga — epic, poetic, with references to the gods and fate.
Keep narrations brief (1-3 sentences) but vivid and atmospheric.""",
    provider = provider,
    producer = skaldProducer,
    bootstrapServers = bootstrapServers,
    maxTokens = maxTokens,
    rateLimiter = rateLimiter
) {
    private val skaldLog = LoggerFactory.getLogger("Skald-Sigurd")
    private val skaldRecentEvents = mutableListOf<WorldEvent>()
    private var lastProcessedTick = -1
    private var latestWorldState: WorldState? = null

    override suspend fun run() {
        skaldLog.info("Sigurd the Skald awakens")

        // Send initial IDLE task so engine knows the Skald is passive
        val initialTask = AgentTask(tick = 0, agentName = name, taskType = TaskType.IDLE, reasoning = "Observing and composing")
        val taskJson = GameJson.encodeToString(AgentTask.serializer(), initialTask)
        withContext(Dispatchers.IO) {
            skaldProducer.send(ProducerRecord(Topics.AGENT_TASKS, name, taskJson))
        }

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
                            try { latestWorldState = GameJson.decodeFromString<WorldState>(record.value()) }
                            catch (_: Exception) {}
                        }
                        Topics.WORLD_EVENTS -> {
                            try {
                                val event = GameJson.decodeFromString<WorldEvent>(record.value())
                                skaldRecentEvents.add(event)
                                if (skaldRecentEvents.size > 20) skaldRecentEvents.removeFirst()
                            } catch (_: Exception) {}
                        }
                    }
                }

                val ws = latestWorldState ?: continue
                if (ws.tick <= lastProcessedTick) continue
                lastProcessedTick = ws.tick

                // Narrate every 2nd tick
                if (ws.tick % 2 != 0) continue

                // Re-send IDLE task periodically so engine doesn't clear it
                if (ws.tick % 10 == 0) {
                    val idleTask = AgentTask(tick = ws.tick, agentName = name, taskType = TaskType.IDLE, reasoning = "Observing and composing")
                    val json = GameJson.encodeToString(AgentTask.serializer(), idleTask)
                    withContext(Dispatchers.IO) {
                        skaldProducer.send(ProducerRecord(Topics.AGENT_TASKS, name, json))
                    }
                }

                val systemPrompt = PromptBuilder.buildSystemPrompt(name, role, personality)
                val userPrompt = PromptBuilder.buildSkaldUserPrompt(ws, skaldRecentEvents)

                rateLimiter?.let { withContext(Dispatchers.IO) { it.acquire() } }
                try {
                    val narration = provider.complete(systemPrompt, userPrompt, maxTokens)
                    val cleanNarration = narration
                        .replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), "")
                        .replace(Regex("\\{.*?}", RegexOption.DOT_MATCHES_ALL), "")
                        .trim()
                    if (cleanNarration.isEmpty()) continue

                    val entry = SagaLogEntry(tick = ws.tick, text = cleanNarration)
                    val json = GameJson.encodeToString(SagaLogEntry.serializer(), entry)
                    withContext(Dispatchers.IO) {
                        skaldProducer.send(ProducerRecord(Topics.SAGA_LOG, name, json))
                    }
                    skaldLog.info("[Tick ${ws.tick}] $cleanNarration")
                } finally {
                    rateLimiter?.release()
                }
            }
        } finally {
            withContext(Dispatchers.IO) { consumer.close() }
            skaldLog.info("Sigurd goes to sleep")
        }
    }
}
