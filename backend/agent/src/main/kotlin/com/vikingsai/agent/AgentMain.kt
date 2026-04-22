package com.vikingsai.agent

import com.vikingsai.agent.agents.*
import com.vikingsai.common.config.GameConfig
import com.vikingsai.common.kafka.createProducer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.Semaphore

fun main() {
    val log = LoggerFactory.getLogger("AgentMain")

    log.info("Viking Settlement — Agent Runner starting...")

    val kafkaConfig = GameConfig.kafka
    val llmConfig = GameConfig.llm
    val provider = GameConfig.createLlmProvider()

    log.info("Kafka: ${kafkaConfig.bootstrapServers}")
    log.info("LLM Provider: ${llmConfig.provider} (model: ${llmConfig.model})")
    log.info("Max tokens: ${llmConfig.maxTokens}")

    val producer = createProducer(kafkaConfig.bootstrapServers)

    // Rate limiter: max 2 concurrent LLM calls to stay within API limits
    val rateLimiter = Semaphore(2)

    val agents = listOf(
        JarlAgent(provider, producer, kafkaConfig.bootstrapServers, llmConfig.maxTokens, rateLimiter),
        WarriorAgent(provider, producer, kafkaConfig.bootstrapServers, llmConfig.maxTokens, rateLimiter),
        FishermanAgent(provider, producer, kafkaConfig.bootstrapServers, llmConfig.maxTokens, rateLimiter),
        ShipbuilderAgent(provider, producer, kafkaConfig.bootstrapServers, llmConfig.maxTokens, rateLimiter),
    )

    log.info("Launching ${agents.size} agents: ${agents.joinToString { "${it.name} (${it.role})" }}")

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down agents...")
        producer.close()
    })

    runBlocking {
        for (agent in agents) {
            launch {
                try {
                    agent.run()
                } catch (e: Exception) {
                    log.error("Agent ${agent.name} crashed: ${e.message}", e)
                }
            }
        }
    }
}
