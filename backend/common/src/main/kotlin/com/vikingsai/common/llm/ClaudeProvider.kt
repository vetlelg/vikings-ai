package com.vikingsai.common.llm

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class ClaudeProvider(
    private val apiKey: String,
    private val model: String = "claude-sonnet-4-20250514"
) : LlmProvider {

    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(systemPrompt: String, userMessage: String, maxTokens: Int): String {
        val requestBody = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("system", systemPrompt)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                }
            }
        }

        val response = client.post("https://api.anthropic.com/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val content = responseBody["content"]?.jsonArray?.firstOrNull()?.jsonObject
        return content?.get("text")?.jsonPrimitive?.content ?: ""
    }
}
