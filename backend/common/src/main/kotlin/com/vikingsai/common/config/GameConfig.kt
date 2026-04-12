package com.vikingsai.common.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.vikingsai.common.llm.*

data class KafkaConfig(
    val bootstrapServers: String
)

data class EngineConfig(
    val tickRateMs: Long,
    val gridWidth: Int,
    val gridHeight: Int
)

data class LlmConfig(
    val provider: String,
    val model: String,
    val apiKey: String,
    val maxTokens: Int
)

data class BridgeConfig(
    val port: Int,
    val replayFile: String
)

object GameConfig {
    private val config: Config = ConfigFactory.load()

    val kafka: KafkaConfig by lazy {
        KafkaConfig(
            bootstrapServers = config.getString("kafka.bootstrap-servers")
        )
    }

    val engine: EngineConfig by lazy {
        EngineConfig(
            tickRateMs = config.getLong("engine.tick-rate-ms"),
            gridWidth = config.getInt("engine.grid-width"),
            gridHeight = config.getInt("engine.grid-height")
        )
    }

    val llm: LlmConfig by lazy {
        LlmConfig(
            provider = config.getString("llm.provider"),
            model = config.getString("llm.model"),
            apiKey = config.getString("llm.api-key"),
            maxTokens = config.getInt("llm.max-tokens")
        )
    }

    val bridge: BridgeConfig by lazy {
        BridgeConfig(
            port = config.getInt("bridge.port"),
            replayFile = config.getString("bridge.replay-file")
        )
    }

    fun createLlmProvider(): LlmProvider {
        return when (llm.provider) {
            "claude" -> ClaudeProvider(llm.apiKey, llm.model)
            "gemini" -> GeminiProvider(llm.apiKey, llm.model)
            "groq" -> GroqProvider(llm.apiKey, llm.model)
            else -> StubProvider()
        }
    }
}
