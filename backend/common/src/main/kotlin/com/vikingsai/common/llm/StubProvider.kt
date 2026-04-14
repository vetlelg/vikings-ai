package com.vikingsai.common.llm

import kotlinx.coroutines.delay

class StubProvider(private val delayMs: Long = 500) : LlmProvider {

    private val sagaNarrations = listOf(
        "The wind whispers through the fjord as the Vikings toil beneath an iron sky. Odin watches from his throne in Asgard.",
        "Smoke rises from the village hearth. The settlers move with purpose, each step a verse in the saga of their new home.",
        "The mountains stand as silent sentinels while Bjorn surveys his domain. The gods have blessed this coastline.",
        "Astrid sharpens her blade by firelight. The wolves will learn to fear the steel of the North.",
        "Erik casts his nets into the dark waters of the fjord. The sea provides, as it always has, as it always will.",
        "Ingrid measures timber with a practiced eye. Every beam she lays is a prayer to the Allfather for strength.",
        "Night descends upon the settlement like a raven's wing. The stars above mirror the embers below.",
        "A storm gathers beyond the mountains. The Vikings know well — the gods test those they favor most.",
        "The colony grows stronger with each passing day. Soon, songs of this settlement will echo in the great halls.",
        "Dawn breaks golden over the fjord. Another day begins in this land of ice and iron and unyielding will.",
        "The forest yields its timber grudgingly, but the Northmen are patient. They have carved kingdoms from less.",
        "Wolves howl in the distance. Let them come — the shield wall of the North has never broken.",
    )

    override suspend fun complete(systemPrompt: String, userMessage: String, maxTokens: Int): String {
        delay(delayMs)

        // Skald narration
        if (userMessage.contains("Write a brief, dramatic narration") ||
            userMessage.contains("Norse saga") ||
            systemPrompt.contains("Skald")
        ) {
            return sagaNarrations.random()
        }

        val ctx = parseContext(userMessage)
        val role = detectRole(systemPrompt)

        return decideTask(role, ctx)
    }

    private data class StubContext(
        val health: Int,
        val inventoryCount: Int,
        val hasInventory: Boolean,
        val onVillage: Boolean,
        val nearestThreatDist: Int?,
        val currentTaskType: String?,
    )

    private fun parseContext(userMessage: String): StubContext {
        val health = Regex("""Health: (\d+)/""").find(userMessage)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 100

        val inventoryLine = Regex("""Inventory: (.+)""").find(userMessage)
            ?.groupValues?.get(1) ?: "empty"
        val hasInventory = inventoryLine != "empty"
        val inventoryCount = if (hasInventory) {
            Regex("""(\d+)""").findAll(inventoryLine).sumOf { it.value.toIntOrNull() ?: 0 }
        } else 0

        val terrain = Regex("""Standing on: (\w+)""").find(userMessage)
            ?.groupValues?.get(1) ?: "GRASS"
        val onVillage = terrain == "VILLAGE"

        val threatSection = userMessage.substringAfter("=== ACTIVE THREATS ===", "")
            .substringBefore("===")
        val threatMatch = Regex("""dist=(\d+)""").find(threatSection)
        val nearestThreatDist = threatMatch?.groupValues?.get(1)?.toIntOrNull()

        val currentTaskType = Regex("""Current task: (\w+)""").find(userMessage)
            ?.groupValues?.get(1)

        return StubContext(health, inventoryCount, hasInventory, onVillage, nearestThreatDist, currentTaskType)
    }

    private fun detectRole(systemPrompt: String): String = when {
        systemPrompt.contains("warrior", ignoreCase = true) -> "WARRIOR"
        systemPrompt.contains("jarl", ignoreCase = true) -> "JARL"
        systemPrompt.contains("fisherman", ignoreCase = true) -> "FISHERMAN"
        systemPrompt.contains("shipbuilder", ignoreCase = true) -> "SHIPBUILDER"
        systemPrompt.contains("skald", ignoreCase = true) -> "SKALD"
        else -> "UNKNOWN"
    }

    private fun decideTask(role: String, ctx: StubContext): String {
        // Priority 1: Fight if threat is close and agent is a fighter
        if (ctx.nearestThreatDist != null && ctx.nearestThreatDist <= 4) {
            if (role == "WARRIOR" || role == "JARL") {
                return task("fight", reasoning = "Threat nearby — engaging!")
            }
            return task("flee", reasoning = "Danger close — retreating!")
        }

        // Priority 2: Low health — idle to heal
        if (ctx.health < 30) {
            return task("idle", reasoning = "Wounded — resting to recover.")
        }

        // Priority 3: Deposit if carrying resources
        if (ctx.hasInventory && ctx.inventoryCount >= 2) {
            return task("deposit", reasoning = "Returning resources to village.")
        }

        // Priority 4: Role-based gathering/activity
        return when (role) {
            "WARRIOR" -> {
                if (ctx.nearestThreatDist != null && ctx.nearestThreatDist <= 12) {
                    task("fight", reasoning = "Hunting the threat!")
                } else {
                    task("gather", targetResource = "FURS", reasoning = "No threats — gathering furs.")
                }
            }
            "JARL" -> task("gather", targetResource = "IRON", reasoning = "Gathering iron for the longship.")
            "FISHERMAN" -> task("gather", targetResource = "FISH", reasoning = "Casting nets in the fjord.")
            "SHIPBUILDER" -> task("gather", targetResource = "TIMBER", reasoning = "Chopping timber for the longship.")
            else -> task("idle", reasoning = "Observing the settlement.")
        }
    }

    private fun task(type: String, targetResource: String? = null, reasoning: String): String {
        val resourcePart = if (targetResource != null) ""","targetResourceType":"$targetResource"""" else ""
        return """{"taskType":"$type"$resourcePart,"reasoning":"$reasoning"}"""
    }
}
