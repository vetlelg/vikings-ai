package com.vikingsai.agent.agents

import com.vikingsai.agent.BaseAgent
import com.vikingsai.agent.prompt.PromptBuilder
import com.vikingsai.common.kafka.GameJson
import com.vikingsai.common.kafka.Topics
import com.vikingsai.common.llm.LlmProvider
import com.vikingsai.common.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.concurrent.Semaphore

/**
 * The Jarl has a dual role:
 * 1. Personal tasks — reactive/deliberative, same as any agent
 * 2. Strategic directives — periodically evaluates colony state and issues
 *    orders to other agents via the agent-directives topic
 *
 * This models hierarchical orchestration: the Jarl publishes intent,
 * other agents interpret and may override based on local conditions.
 */
class JarlAgent(
    provider: LlmProvider,
    private val jarlProducer: KafkaProducer<String, String>,
    bootstrapServers: String,
    maxTokens: Int,
    rateLimiter: Semaphore?
) : BaseAgent(
    name = "Bjorn",
    role = AgentRole.JARL,
    personality = """You are the Jarl — the leader of this Viking settlement. You speak with authority and gravitas.
Your priorities:
1. DEFEND the settlement. If a dragon or raiders appear, rally the warriors and coordinate defense.
2. Ensure the colony gathers enough resources to build the longship. Direct others by speaking commands.
3. Stay near the VILLAGE center to oversee operations.
4. If there are no threats, inspect the settlement or gather resources yourself.
You are brave but strategic. You do not flee unless the situation is hopeless.
When you speak, you give orders and encouragement to your people.""",
    provider = provider,
    producer = jarlProducer,
    bootstrapServers = bootstrapServers,
    maxTokens = maxTokens,
    rateLimiter = rateLimiter
) {
    private val jarlLog = LoggerFactory.getLogger("Jarl-Bjorn")
    private var lastDirectiveTick = -1

    companion object {
        private const val DIRECTIVE_INTERVAL = 10
    }

    override suspend fun onTick(self: AgentSnapshot, ws: WorldState) {
        if (self.status == AgentStatus.DEAD) return
        if (ws.tick < DIRECTIVE_INTERVAL) return
        if (ws.tick - lastDirectiveTick < DIRECTIVE_INTERVAL) return

        try {
            issueDirective(ws)
            lastDirectiveTick = ws.tick
        } catch (e: Exception) {
            jarlLog.error("Failed to issue directive at tick ${ws.tick}: ${e.message}")
        }
    }

    private suspend fun issueDirective(ws: WorldState) {
        val systemPrompt = PromptBuilder.buildJarlStrategicSystemPrompt()
        val userPrompt = PromptBuilder.buildJarlStrategicUserPrompt(ws, recentEvents, peerObservations)

        rateLimiter?.let { withContext(Dispatchers.IO) { it.acquire() } }
        try {
            val response = provider.complete(systemPrompt, userPrompt, maxTokens)
            val directive = parseDirective(response, ws.tick)

            if (directive != null) {
                val json = GameJson.encodeToString(JarlDirective.serializer(), directive)
                withContext(Dispatchers.IO) {
                    jarlProducer.send(ProducerRecord(Topics.AGENT_DIRECTIVES, name, json))
                }
                jarlLog.info("[Tick ${ws.tick}] DIRECTIVE: ${directive.assessment.take(80)}")
                for (a in directive.assignments) {
                    jarlLog.info("  → ${a.agentName}: ${a.directive}")
                }
            }
        } finally {
            rateLimiter?.release()
        }
    }

    private fun parseDirective(response: String, tick: Int): JarlDirective? {
        return try {
            val jsonStr = extractJsonBlock(response)
            val json = Json.parseToJsonElement(jsonStr).jsonObject

            val assessment = json["assessment"]?.jsonPrimitive?.content ?: return null
            val assignments = json["assignments"]?.jsonArray?.mapNotNull { elem ->
                val obj = elem.jsonObject
                val agentName = obj["agentName"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val directive = obj["directive"]?.jsonPrimitive?.content ?: return@mapNotNull null
                AgentAssignment(agentName = agentName, directive = directive)
            } ?: emptyList()

            JarlDirective(tick = tick, assessment = assessment, assignments = assignments)
        } catch (e: Exception) {
            jarlLog.warn("Failed to parse directive response: ${e.message}. Raw: ${response.take(100)}")
            null
        }
    }

    private fun extractJsonBlock(text: String): String {
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?(\\{.*?})\\s*\\n?```", RegexOption.DOT_MATCHES_ALL)
        codeBlockPattern.find(text)?.let { return it.groupValues[1].trim() }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) return text.substring(start, end + 1)
        return text.trim()
    }
}
