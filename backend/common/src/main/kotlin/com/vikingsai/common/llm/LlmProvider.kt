package com.vikingsai.common.llm

interface LlmProvider {
    suspend fun complete(systemPrompt: String, userMessage: String, maxTokens: Int): String
}
