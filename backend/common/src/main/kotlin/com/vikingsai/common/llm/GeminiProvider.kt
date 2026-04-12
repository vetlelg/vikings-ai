package com.vikingsai.common.llm

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash"
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
                putJsonObject("thinkingConfig") {
                    put("thinkingBudget", 0)
                }
            }
        }

        val response = client.post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        ) {
            parameter("key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            System.err.println("Gemini API error (${response.status}): $body")
            return ""
        }
        val responseBody = json.parseToJsonElement(body).jsonObject
        val candidates = responseBody["candidates"]?.jsonArray
        val parts = candidates?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
        // Gemini 2.5 may return thinking parts before the text part — grab the last text part
        val textPart = parts?.lastOrNull { it.jsonObject.containsKey("text") }?.jsonObject
        return textPart?.get("text")?.jsonPrimitive?.content ?: ""
    }
}
