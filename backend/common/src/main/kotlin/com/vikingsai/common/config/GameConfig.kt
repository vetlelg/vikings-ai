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

    private val defaultModels = mapOf(
        "claude" to "claude-sonnet-4-20250514",
        "gemini" to "gemini-2.5-flash",
        "groq" to "llama-3.1-8b-instant"
    )

    fun createLlmProvider(): LlmProvider {
        val model = if (System.getenv("LLM_MODEL") != null) llm.model
                     else defaultModels[llm.provider] ?: llm.model
        return when (llm.provider) {
            "claude" -> ClaudeProvider(llm.apiKey, model)
            "gemini" -> GeminiProvider(llm.apiKey, model)
            "groq" -> GroqProvider(llm.apiKey, model)
            else -> StubProvider()
        }
    }
}
