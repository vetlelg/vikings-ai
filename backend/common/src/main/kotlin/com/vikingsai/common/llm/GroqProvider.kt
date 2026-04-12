package com.vikingsai.common.llm

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class GroqProvider(
    private val apiKey: String,
    private val model: String = "llama-3.1-8b-instant"
) : LlmProvider {

    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(systemPrompt: String, userMessage: String, maxTokens: Int): String {
        val requestBody = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                }
            }
        }

        val response = client.post("https://api.groq.com/openai/v1/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val choices = responseBody["choices"]?.jsonArray
        val message = choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        return message?.get("content")?.jsonPrimitive?.content ?: ""
    }
}
