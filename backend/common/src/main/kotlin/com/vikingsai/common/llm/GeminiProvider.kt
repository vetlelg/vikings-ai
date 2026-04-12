package com.vikingsai.common.llm

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-2.0-flash-lite"
) : LlmProvider {

    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(systemPrompt: String, userMessage: String, maxTokens: Int): String {
        val requestBody = buildJsonObject {
            putJsonObject("system_instruction") {
                putJsonArray("parts") {
                    addJsonObject { put("text", systemPrompt) }
                }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", userMessage) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", maxTokens)
            }
        }

        val response = client.post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        ) {
            parameter("key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val candidates = responseBody["candidates"]?.jsonArray
        val content = candidates?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?.firstOrNull()?.jsonObject
        return content?.get("text")?.jsonPrimitive?.content ?: ""
    }
}
