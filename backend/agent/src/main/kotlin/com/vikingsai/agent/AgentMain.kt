package com.vikingsai.agent

import com.vikingsai.common.config.GameConfig
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

fun main() {
    val log = LoggerFactory.getLogger("AgentMain")
    log.info("RTS AI — Agent system starting...")

    val kafkaConfig = GameConfig.kafka
    val llmConfig = GameConfig.llm
    val provider = GameConfig.createLlmProvider()

    log.info("LLM provider: ${llmConfig.provider} (${llmConfig.model})")

    val manager = AgentManager(
        bootstrapServers = kafkaConfig.bootstrapServers,
        provider = provider,
        maxTokens = llmConfig.maxTokens,
    )

    runBlocking {
        manager.run()
    }
}
