package com.vikingsai.agent.agents

import com.vikingsai.agent.BaseAgent
import com.vikingsai.agent.prompt.PromptBuilder
import com.vikingsai.common.kafka.GameJson
import com.vikingsai.common.kafka.Topics
import com.vikingsai.common.llm.LlmProvider
import com.vikingsai.common.model.AgentRole
import com.vikingsai.common.model.SagaLogEntry
import com.vikingsai.common.model.WorldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.concurrent.Semaphore

/**
 * The Skald does not gather resources or fight.
 * Instead, it observes the world and publishes dramatic narrations to the saga-log topic.
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
    private val skaldRecentEvents = mutableListOf<com.vikingsai.common.model.WorldEvent>()

    override suspend fun processTickAction(worldState: WorldState) {
        // Skald narrates every 2nd tick to avoid spamming
        if (worldState.tick % 2 != 0) return

        val systemPrompt = PromptBuilder.buildSystemPrompt(name, role, personality)
        val userPrompt = PromptBuilder.buildSkaldUserPrompt(worldState, skaldRecentEvents)

        rateLimiter?.let { withContext(Dispatchers.IO) { it.acquire() } }
        try {
            val narration = provider.complete(systemPrompt, userPrompt, maxTokens)

            // Clean up the narration — remove any JSON or markdown artifacts
            val cleanNarration = narration
                .replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("\\{.*?}", RegexOption.DOT_MATCHES_ALL), "")
                .trim()
                .ifEmpty { return }

            val entry = SagaLogEntry(
                tick = worldState.tick,
                text = cleanNarration
            )

            val json = GameJson.encodeToString(SagaLogEntry.serializer(), entry)
            withContext(Dispatchers.IO) {
                skaldProducer.send(ProducerRecord(Topics.SAGA_LOG, name, json))
            }

            skaldLog.info("[Tick ${worldState.tick}] $cleanNarration")
        } finally {
            rateLimiter?.release()
        }
    }
}
